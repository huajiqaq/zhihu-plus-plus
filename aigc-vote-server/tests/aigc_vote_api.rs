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

use aigc_vote_server::{
    AigcFlagEvidence, AigcFlagRequest, AigcFlagResponse, AppState, ReadEvent, ReadEventsRequest,
    ReadEventsResponse, VoterIdentity, app,
};
use axum::body::{Body, to_bytes};
use http::{Method, Request, StatusCode};
use serde::de::DeserializeOwned;
use serde_json::json;
use tower::ServiceExt;

#[tokio::test]
async fn browser_preflight_only_allows_known_origins_and_json_without_credentials() {
    let app = app(AppState::new_in_memory().expect("初始化测试状态失败"));
    for (origin, method, headers, allowed) in [
        ("https://zly2006.github.io", "POST", "content-type", true),
        ("http://127.0.0.1:8080", "GET", "Content-Type", true),
        ("https://other.example", "POST", "content-type", false),
        ("http://127.0.0.1:8081", "POST", "content-type", false),
        ("https://zly2006.github.io", "DELETE", "content-type", false),
        ("https://zly2006.github.io", "POST", "authorization", false),
    ] {
        let response = app.clone().oneshot(
            Request::builder().method(Method::OPTIONS).uri("/v1/read-events:batch")
                .header("origin", origin)
                .header("access-control-request-method", method)
                .header("access-control-request-headers", headers)
                .body(Body::empty()).unwrap(),
        ).await.unwrap();
        assert_eq!(response.status(), if allowed { StatusCode::NO_CONTENT } else { StatusCode::FORBIDDEN });
        assert_eq!(response.headers().get("access-control-allow-origin").map(|value| value.to_str().unwrap()), allowed.then_some(origin));
        assert!(!response.headers().contains_key("access-control-allow-credentials"));
    }
}

#[tokio::test]
async fn read_events_award_credit_per_20_valid_unique_contents_and_cap_at_five() {
    let state = AppState::new_in_memory().expect("初始化测试状态失败");
    let app = app(state);

    let first_response: ReadEventsResponse = request_json(
        app.clone(),
        Method::POST,
        "/v1/read-events:batch",
        &ReadEventsRequest {
            client_id: "client-a".to_string(),
            events: build_read_events(21, 30_000, 0.75),
        },
    )
    .await;

    assert_eq!(first_response.credit, 2);
    assert_eq!(first_response.progress, 1);
    assert_eq!(first_response.cap, 5);
    assert_eq!(first_response.accepted_events, 21);

    let duplicate_response: ReadEventsResponse = request_json(
        app.clone(),
        Method::POST,
        "/v1/read-events:batch",
        &ReadEventsRequest {
            client_id: "client-a".to_string(),
            events: build_read_events(21, 30_000, 0.75),
        },
    )
    .await;

    assert_eq!(duplicate_response.credit, 2);
    assert_eq!(duplicate_response.progress, 1);
    assert_eq!(duplicate_response.accepted_events, 0);

    let capped_response: ReadEventsResponse = request_json(
        app,
        Method::POST,
        "/v1/read-events:batch",
        &ReadEventsRequest {
            client_id: "client-a".to_string(),
            events: build_read_events_from(21, 120, 30_000, 0.75),
        },
    )
    .await;

    assert_eq!(capped_response.credit, 5);
    assert_eq!(capped_response.progress, 0);
    assert_eq!(capped_response.cap, 5);
}

#[tokio::test]
async fn short_or_unread_like_events_do_not_earn_credit() {
    let state = AppState::new_in_memory().expect("初始化测试状态失败");
    let app = app(state);

    let response: ReadEventsResponse = request_json(
        app,
        Method::POST,
        "/v1/read-events:batch",
        &ReadEventsRequest {
            client_id: "client-a".to_string(),
            events: build_read_events(150, 2_000, 0.05),
        },
    )
    .await;

    assert_eq!(response.credit, 1);
    assert_eq!(response.progress, 0);
    assert_eq!(response.accepted_events, 0);
    assert_eq!(response.rejected_events, 150);
}

#[tokio::test]
async fn read_events_without_content_html_do_not_earn_credit() {
    let state = AppState::new_in_memory().expect("初始化测试状态失败");
    let app = app(state);

    let response: ReadEventsResponse = request_json(
        app,
        Method::POST,
        "/v1/read-events:batch",
        &ReadEventsRequest {
            client_id: "client-a".to_string(),
            events: vec![ReadEvent {
                content_type: "answer".to_string(),
                content_id: "42".to_string(),
                title: "测试回答".to_string(),
                author_hash: "author-hash".to_string(),
                content_html: String::new(),
                content_updated_at: 1_781_000_000,
                opened_at: 1_781_020_000,
                foreground_duration_ms: 45_000,
                max_scroll_ratio: 0.82,
            }],
        },
    )
    .await;

    assert_eq!(response.credit, 1);
    assert_eq!(response.progress, 0);
    assert_eq!(response.accepted_events, 0);
    assert_eq!(response.rejected_events, 1);
}

#[tokio::test]
async fn aigc_flag_uploads_content_snapshot_consumes_credit_and_is_idempotent() {
    let state = AppState::new_in_memory().expect("初始化测试状态失败");
    let app = app(state);

    let _: ReadEventsResponse = request_json(
        app.clone(),
        Method::POST,
        "/v1/read-events:batch",
        &ReadEventsRequest {
            client_id: "client-a".to_string(),
            events: build_read_events(20, 30_000, 0.75),
        },
    )
    .await;

    let flag_request = AigcFlagRequest {
        client_id: "client-a".to_string(),
        voter: test_voter("voter-a", "投票人 A"),
        title: "测试回答".to_string(),
        author_hash: "author-hash".to_string(),
        content_html: "<p>这是一段需要被服务端留存版本的正文。</p>".to_string(),
        content_updated_at: 1_781_020_000,
        evidence: AigcFlagEvidence {
            client_view_duration_ms: 45_000,
            scroll_depth: 0.82,
            opened_at: 1_781_020_123,
        },
    };

    let first_flag: AigcFlagResponse = request_json(
        app.clone(),
        Method::POST,
        "/v1/contents/answer/42/aigc-flag",
        &flag_request,
    )
    .await;

    assert!(first_flag.my_flagged);
    assert_eq!(first_flag.credit, 1);
    assert_eq!(first_flag.effective_flag_count, 1);
    assert_eq!(first_flag.current_version_flag_count, 1);
    assert_eq!(first_flag.content_updated_at, 1_781_020_000);
    assert_eq!(first_flag.content_hash.len(), 64);
    assert!(!first_flag.credit_bypass_available);
    assert_eq!(first_flag.voters.len(), 1);
    assert_eq!(first_flag.voters[0].voter_id, "voter-a");
    assert_eq!(first_flag.voters[0].voter_name, "投票人 A");
    assert!(!first_flag.voters[0].credit_bypassed);

    let second_flag: AigcFlagResponse = request_json(
        app,
        Method::POST,
        "/v1/contents/answer/42/aigc-flag",
        &flag_request,
    )
    .await;

    assert!(second_flag.my_flagged);
    assert_eq!(second_flag.credit, 1);
    assert_eq!(second_flag.effective_flag_count, 1);
    assert_eq!(second_flag.current_version_flag_count, 1);
    assert_eq!(second_flag.content_hash, first_flag.content_hash);
}

#[tokio::test]
async fn different_named_voters_on_same_client_can_flag_the_same_content() {
    let state = AppState::new_in_memory().expect("初始化测试状态失败");
    let app = app(state);

    let _: ReadEventsResponse = request_json(
        app.clone(),
        Method::POST,
        "/v1/read-events:batch",
        &ReadEventsRequest {
            client_id: "shared-client".to_string(),
            events: build_read_events(40, 30_000, 0.75),
        },
    )
    .await;

    let first_flag: AigcFlagResponse = request_json(
        app.clone(),
        Method::POST,
        "/v1/contents/answer/42/aigc-flag",
        &test_flag_request("shared-client", test_voter("voter-a", "投票人 A")),
    )
    .await;

    let second_flag: AigcFlagResponse = request_json(
        app,
        Method::POST,
        "/v1/contents/answer/42/aigc-flag",
        &test_flag_request("shared-client", test_voter("voter-b", "投票人 B")),
    )
    .await;

    assert_eq!(first_flag.credit, 2);
    assert_eq!(first_flag.effective_flag_count, 1);
    assert_eq!(second_flag.credit, 1);
    assert_eq!(second_flag.effective_flag_count, 2);
    assert_eq!(second_flag.voters.len(), 2);
}

#[tokio::test]
async fn aigc_flag_without_credit_is_rejected() {
    let state = AppState::new_in_memory().expect("初始化测试状态失败");
    let app = app(state);

    let _: AigcFlagResponse = request_json(
        app.clone(),
        Method::POST,
        "/v1/contents/answer/41/aigc-flag",
        &test_flag_request(
            "client-without-credit",
            test_voter("first-voter", "首赠积分投票人"),
        ),
    )
    .await;

    let response = app
        .oneshot(
            Request::builder()
                .method(Method::POST)
                .uri("/v1/contents/answer/42/aigc-flag")
                .header("content-type", "application/json")
                .body(Body::from(
                    json!({
                        "client_id": "client-without-credit",
                        "voter": {
                            "id": "voter-without-credit",
                            "name": "无积分用户",
                            "url_token": "voter-without-credit",
                            "avatar_url": null
                        },
                        "title": "测试回答",
                        "author_hash": "author-hash",
                        "content_html": "<p>正文</p>",
                        "content_updated_at": 1781020000,
                        "evidence": {
                            "client_view_duration_ms": 45000,
                            "scroll_depth": 0.82,
                            "opened_at": 1781020123
                        }
                    })
                    .to_string(),
                ))
                .expect("构造测试请求失败"),
        )
        .await
        .expect("获取测试响应失败");

    assert_eq!(response.status(), StatusCode::PAYMENT_REQUIRED);
}

#[tokio::test]
async fn aigc_flag_without_read_evidence_is_rejected_for_regular_voter() {
    let state = AppState::new_in_memory().expect("初始化测试状态失败");
    let app = app(state);

    let response = app
        .oneshot(
            Request::builder()
                .method(Method::POST)
                .uri("/v1/contents/answer/42/aigc-flag")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_string(&test_flag_request_with_evidence(
                        "regular-client",
                        test_voter("regular-voter", "普通用户"),
                        2_000,
                        0.05,
                    ))
                    .expect("序列化测试请求体失败"),
                ))
                .expect("构造测试请求失败"),
        )
        .await
        .expect("获取测试响应失败");

    assert_eq!(response.status(), StatusCode::BAD_REQUEST);
}

#[tokio::test]
async fn configured_named_voter_can_flag_without_credit() {
    let state = AppState::new_in_memory_with_credit_bypass_voters(
        ["owner-token".to_string()].into_iter().collect(),
    )
    .expect("初始化测试状态失败");
    let app = app(state);

    let _: AigcFlagResponse = request_json(
        app.clone(),
        Method::POST,
        "/v1/contents/answer/41/aigc-flag",
        &test_flag_request(
            "client-without-credit",
            test_voter("first-voter", "首赠积分投票人"),
        ),
    )
    .await;

    let flag: AigcFlagResponse = request_json(
        app,
        Method::POST,
        "/v1/contents/answer/42/aigc-flag",
        &AigcFlagRequest {
            client_id: "client-without-credit".to_string(),
            voter: VoterIdentity {
                id: "owner-id".to_string(),
                name: "Owner".to_string(),
                url_token: Some("owner-token".to_string()),
                avatar_url: Some("https://pic.example/avatar.jpg".to_string()),
            },
            title: "测试回答".to_string(),
            author_hash: "author-hash".to_string(),
            content_html: "<p>正文</p>".to_string(),
            content_updated_at: 1_781_020_000,
            evidence: AigcFlagEvidence {
                client_view_duration_ms: 2_000,
                scroll_depth: 0.05,
                opened_at: 1_781_020_123,
            },
        },
    )
    .await;

    assert!(flag.my_flagged);
    assert!(flag.credit_bypass_available);
    assert_eq!(flag.credit, 0);
    assert_eq!(flag.effective_flag_count, 1);
    assert_eq!(flag.voters[0].voter_id, "owner-id");
    assert!(flag.voters[0].credit_bypassed);
}

async fn request_json<T, B>(app: axum::Router, method: Method, uri: &str, body: &B) -> T
where
    T: DeserializeOwned,
    B: serde::Serialize,
{
    let response = app
        .oneshot(
            Request::builder()
                .method(method)
                .uri(uri)
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_string(body).expect("序列化测试请求体失败"),
                ))
                .expect("构造测试请求失败"),
        )
        .await
        .expect("获取测试响应失败");

    assert_eq!(response.status(), StatusCode::OK);
    let bytes = to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("读取测试响应体失败");
    serde_json::from_slice(&bytes).expect("解析测试 JSON 响应失败")
}

fn build_read_events(count: usize, duration_ms: i64, scroll_depth: f64) -> Vec<ReadEvent> {
    build_read_events_from(0, count, duration_ms, scroll_depth)
}

fn build_read_events_from(
    start: usize,
    count: usize,
    duration_ms: i64,
    scroll_depth: f64,
) -> Vec<ReadEvent> {
    (start..start + count)
        .map(|idx| ReadEvent {
            content_type: "answer".to_string(),
            content_id: idx.to_string(),
            title: format!("测试回答 {idx}"),
            author_hash: "author-hash".to_string(),
            content_html: format!("<p>用于阅读积分同步的正文 {idx}</p>"),
            content_updated_at: 1_781_000_000 + idx as i64,
            opened_at: 1_781_020_000 + idx as i64,
            foreground_duration_ms: duration_ms,
            max_scroll_ratio: scroll_depth,
        })
        .collect()
}

fn test_voter(id: &str, name: &str) -> VoterIdentity {
    VoterIdentity {
        id: id.to_string(),
        name: name.to_string(),
        url_token: Some(id.to_string()),
        avatar_url: None,
    }
}

fn test_flag_request(client_id: &str, voter: VoterIdentity) -> AigcFlagRequest {
    test_flag_request_with_evidence(client_id, voter, 45_000, 0.82)
}

fn test_flag_request_with_evidence(
    client_id: &str,
    voter: VoterIdentity,
    client_view_duration_ms: i64,
    scroll_depth: f64,
) -> AigcFlagRequest {
    AigcFlagRequest {
        client_id: client_id.to_string(),
        voter,
        title: "测试回答".to_string(),
        author_hash: "author-hash".to_string(),
        content_html: "<p>这是一段需要被服务端留存版本的正文。</p>".to_string(),
        content_updated_at: 1_781_020_000,
        evidence: AigcFlagEvidence {
            client_view_duration_ms,
            scroll_depth,
            opened_at: 1_781_020_123,
        },
    }
}
