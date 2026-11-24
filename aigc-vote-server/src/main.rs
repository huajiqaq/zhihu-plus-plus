/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

use std::env;
use std::net::SocketAddr;

use aigc_vote_server::{AppState, parse_credit_bypass_voters, serve};

#[tokio::main]
async fn main() {
    let database_url =
        env::var("AIGC_VOTE_DATABASE_URL").expect("AIGC_VOTE_DATABASE_URL must be configured");
    let credit_bypass_voters =
        parse_credit_bypass_voters(&env::var("AIGC_VOTE_CREDIT_BYPASS_VOTERS").unwrap_or_default());
    let listen_addr = env::var("AIGC_VOTE_LISTEN")
        .unwrap_or_else(|_| "127.0.0.1:8787".to_string())
        .parse::<SocketAddr>()
        .expect("AIGC_VOTE_LISTEN must be a socket address");

    let state =
        AppState::new_postgres_with_credit_bypass_voters(&database_url, credit_bypass_voters)
            .await
            .expect("initialize database");
    serve(state, listen_addr)
        .await
        .expect("serve AIGC vote API");
}
