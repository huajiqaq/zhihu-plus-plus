#!/usr/bin/env python3
# Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
# Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation (version 3 only).
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

"""查找 Zhihu++ 中结构相似的 Kotlin 函数体。

入口：python3 tools/find_similar_kotlin_functions.py --help。
输出仅是待审查候选队列。相似度基于归一化词元片段，
每对候选仍须结合源码核实。
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

from kotlin_scan_common import (
    FUNCTION_RE,
    MODIFIER_RE,
    body_span,
    line_number,
    line_starts,
    mask_comments_and_literals,
    matching_paren,
    parameter_arity,
)


DEFAULT_SOURCE_ROOTS = ("*/src/*/java", "*/src/*/kotlin")
TEST_SOURCE_SET_RE = re.compile(r"/src/(?:test[A-Za-z0-9_]*|[A-Za-z0-9_]*Test[A-Za-z0-9_]*)/")
SKIP_DIRS = {".git", ".gradle", ".idea", ".worktrees", "build", ".kotlin"}

KOTLIN_KEYWORDS = {
    "as",
    "as?",
    "break",
    "class",
    "continue",
    "do",
    "else",
    "false",
    "for",
    "fun",
    "if",
    "in",
    "!in",
    "interface",
    "is",
    "!is",
    "null",
    "object",
    "package",
    "return",
    "super",
    "this",
    "throw",
    "true",
    "try",
    "typealias",
    "typeof",
    "val",
    "var",
    "when",
    "while",
    "by",
    "catch",
    "constructor",
    "delegate",
    "dynamic",
    "field",
    "file",
    "finally",
    "get",
    "import",
    "init",
    "param",
    "property",
    "receiver",
    "set",
    "setparam",
    "where",
}

TOKEN_RE = re.compile(
    r"""
    [A-Za-z_][A-Za-z0-9_]* |
    \d+(?:\.\d+)? |
    ==|!=|<=|>=|&&|\|\||->|=>|::|\?:|[{}()\[\].,;:+\-*/%<>=!?]
    """,
    re.VERBOSE,
)


@dataclass(frozen=True)
class FunctionBody:
    path: str
    line: int
    name: str
    arity: int
    modifiers: str
    kind: str
    body_lines: int
    body_preview: str
    tokens: tuple[str, ...]
    shingles: frozenset[tuple[str, ...]]


def source_files(root: Path, include_tests: bool) -> list[Path]:
    files: list[Path] = []
    for source_root in DEFAULT_SOURCE_ROOTS:
        for base in sorted(root.glob(source_root)):
            for path in sorted(base.rglob("*.kt")):
                rel = "/" + path.relative_to(root).as_posix()
                if any(part in SKIP_DIRS for part in path.relative_to(root).parts):
                    continue
                if not include_tests and TEST_SOURCE_SET_RE.search(rel):
                    continue
                files.append(path)
    return sorted(files)


def normalize_tokens(body: str) -> tuple[str, ...]:
    masked = mask_comments_and_literals(body, keep_literal_tokens=True)
    tokens: list[str] = []
    for token in TOKEN_RE.findall(masked):
        if re.fullmatch(r"\d+(?:\.\d+)?", token):
            tokens.append("NUM")
        elif token in {"STR", "CHAR"}:
            tokens.append(token)
        elif re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", token):
            tokens.append(token if token in KOTLIN_KEYWORDS else "ID")
        else:
            tokens.append(token)
    return tuple(tokens)


def make_shingles(tokens: tuple[str, ...], shingle_size: int) -> frozenset[tuple[str, ...]]:
    if len(tokens) < shingle_size:
        return frozenset()
    return frozenset(tuple(tokens[i : i + shingle_size]) for i in range(len(tokens) - shingle_size + 1))


def parse_functions(
    root: Path,
    files: list[Path],
    min_lines: int,
    min_tokens: int,
    shingle_size: int,
    include_abstract: bool,
) -> list[FunctionBody]:
    functions: list[FunctionBody] = []

    for path in files:
        text = path.read_text(errors="replace")
        masked = mask_comments_and_literals(text, keep_literal_tokens=False)
        starts = line_starts(text)

        for match in FUNCTION_RE.finditer(masked):
            open_pos = masked.find("(", match.end("name"))
            close_pos = matching_paren(masked, open_pos)
            if close_pos < 0:
                continue

            kind, body_start, body_end = body_span(masked, close_pos)
            if kind == "abstract" and not include_abstract:
                continue

            body = text[body_start:body_end]
            body_lines = 0 if kind == "abstract" else max(1, body.count("\n") + 1)
            tokens = normalize_tokens(body)
            if body_lines < min_lines or len(tokens) < min_tokens:
                continue

            shingles = make_shingles(tokens, shingle_size)
            if not shingles:
                continue

            functions.append(
                FunctionBody(
                    path=path.relative_to(root).as_posix(),
                    line=line_number(starts, match.start()),
                    name=match.group("name").strip("`"),
                    arity=parameter_arity(masked[open_pos + 1 : close_pos]),
                    modifiers=" ".join(MODIFIER_RE.findall(match.group("prefix") or "")),
                    kind=kind,
                    body_lines=body_lines,
                    body_preview=" ".join(body.split())[:220],
                    tokens=tokens,
                    shingles=shingles,
                ),
            )

    return functions


def find_similar_pairs(
    functions: list[FunctionBody],
    threshold: float,
    max_bucket_size: int,
    limit: int,
) -> list[tuple[float, int, int, FunctionBody, FunctionBody]]:
    index: dict[tuple[str, ...], list[int]] = defaultdict(list)
    for idx, function in enumerate(functions):
        for shingle in function.shingles:
            index[shingle].append(idx)

    shared_counts: dict[tuple[int, int], int] = defaultdict(int)
    for ids in index.values():
        if len(ids) < 2 or len(ids) > max_bucket_size:
            continue
        for left_pos, left in enumerate(ids):
            for right in ids[left_pos + 1 :]:
                shared_counts[(left, right)] += 1

    pairs: list[tuple[float, int, int, FunctionBody, FunctionBody]] = []
    for (left, right), shared in shared_counts.items():
        left_function = functions[left]
        right_function = functions[right]
        union = len(left_function.shingles) + len(right_function.shingles) - shared
        if union <= 0:
            continue
        score = shared / union
        if score < threshold:
            continue
        pairs.append((score, shared, union, left_function, right_function))

    pairs.sort(
        key=lambda item: (
            -item[0],
            -(item[3].body_lines + item[4].body_lines),
            item[3].path,
            item[3].line,
            item[4].path,
            item[4].line,
        ),
    )
    return pairs[:limit]


def write_pairs(
    pairs: list[tuple[float, int, int, FunctionBody, FunctionBody]],
    output: Path | None,
) -> None:
    header = [
        "score",
        "shared_shingles",
        "union_shingles",
        "tokens_a",
        "tokens_b",
        "body_lines_a",
        "body_lines_b",
        "modifiers_a",
        "name_a",
        "arity_a",
        "path_a",
        "line_a",
        "modifiers_b",
        "name_b",
        "arity_b",
        "path_b",
        "line_b",
        "body_preview_a",
        "body_preview_b",
    ]
    rows = []
    for score, shared, union, left, right in pairs:
        rows.append(
            [
                f"{score:.4f}",
                shared,
                union,
                len(left.tokens),
                len(right.tokens),
                left.body_lines,
                right.body_lines,
                left.modifiers,
                left.name,
                left.arity,
                left.path,
                left.line,
                right.modifiers,
                right.name,
                right.arity,
                right.path,
                right.line,
                left.body_preview,
                right.body_preview,
            ],
        )

    target = output.open("w", newline="") if output else sys.stdout
    try:
        writer = csv.writer(target, delimiter="\t", lineterminator="\n")
        writer.writerow(header)
        writer.writerows(rows)
    finally:
        if output:
            target.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="仓库根目录，默认为当前目录。")
    parser.add_argument("--include-tests", action="store_true", help="扫描时纳入测试源集。")
    parser.add_argument("--threshold", type=float, default=0.82, help="归一化词元片段的最低 Jaccard 相似度。")
    parser.add_argument("--shingle-size", type=int, default=5, help="每个片段包含的归一化词元数。")
    parser.add_argument("--min-lines", type=int, default=4, help="参与比较的函数体最少行数。")
    parser.add_argument("--min-tokens", type=int, default=28, help="参与比较的最少归一化词元数。")
    parser.add_argument("--max-bucket-size", type=int, default=200, help="片段出现次数超过此值时忽略，排除过于常见的片段。")
    parser.add_argument("--limit", type=int, default=500, help="最多输出的候选函数对行数。")
    parser.add_argument("--include-abstract", action="store_true", help="纳入抽象函数和接口声明。")
    parser.add_argument("--output", type=Path, help="将 TSV 输出写入此路径，而非标准输出。")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    files = source_files(root, include_tests=args.include_tests)
    functions = parse_functions(
        root=root,
        files=files,
        min_lines=args.min_lines,
        min_tokens=args.min_tokens,
        shingle_size=args.shingle_size,
        include_abstract=args.include_abstract,
    )
    pairs = find_similar_pairs(
        functions=functions,
        threshold=args.threshold,
        max_bucket_size=args.max_bucket_size,
        limit=args.limit,
    )
    write_pairs(pairs, args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
