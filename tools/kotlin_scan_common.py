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

"""两个 Kotlin 候选扫描器共用的轻量解析原语，不代替编译器语义分析。"""

from __future__ import annotations

import bisect
import re


FUNCTION_RE = re.compile(
    r"""
    (?P<prefix>
        (?:^|[\s;{}])
        (?:@[\w.]+(?:\([^)]*\))?\s*)*
        (?:
            (?:public|private|protected|internal|actual|expect|override|open|abstract|
               final|external|inline|suspend|tailrec|operator|infix)
            \s+
        )*
    )
    fun\s+
    (?:<[^>{}()]*>\s*)?
    (?:(?P<receiver>[A-Za-z_][\w<>?,. ]*)\.)?
    (?P<name>`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)\s*\(
    """,
    re.VERBOSE,
)

MODIFIER_RE = re.compile(
    r"\b(public|private|protected|internal|actual|expect|override|open|abstract|"
    r"final|external|inline|suspend|tailrec|operator|infix)\b",
)


def mask_comments_and_literals(text: str, keep_literal_tokens: bool = False) -> str:
    out: list[str] = []
    i = 0
    state: str | None = None
    triple = False

    while i < len(text):
        char = text[i]
        pair = text[i : i + 2]

        if state is None:
            if pair == "//":
                end = text.find("\n", i)
                if end < 0:
                    out.extend(" " * (len(text) - i))
                    break
                out.extend(" " * (end - i))
                i = end
                continue
            if pair == "/*":
                end = text.find("*/", i + 2)
                end = len(text) if end < 0 else end + 2
                out.extend("\n" if c == "\n" else " " for c in text[i:end])
                i = end
                continue
            if text.startswith('"""', i):
                if keep_literal_tokens:
                    out.append(" STR ")
                out.extend("   ")
                i += 3
                state = "string"
                triple = True
                continue
            if char == '"':
                if keep_literal_tokens:
                    out.append(" STR ")
                out.append(" ")
                i += 1
                state = "string"
                triple = False
                continue
            if char == "'":
                if keep_literal_tokens:
                    out.append(" CHAR ")
                out.append(" ")
                i += 1
                state = "char"
                continue
            out.append(char)
            i += 1
            continue

        if state == "string":
            if triple and text.startswith('"""', i):
                out.extend("   ")
                i += 3
                state = None
                continue
            if not triple and char == "\\":
                out.extend("  ")
                i += 2
                continue
            if not triple and char == '"':
                out.append(" ")
                i += 1
                state = None
                continue
            out.append("\n" if char == "\n" else " ")
            i += 1
            continue

        if state == "char":
            if char == "\\":
                out.extend("  ")
                i += 2
                continue
            if char == "'":
                out.append(" ")
                i += 1
                state = None
                continue
            out.append("\n" if char == "\n" else " ")
            i += 1

    return "".join(out)


def matching_paren(text: str, open_pos: int) -> int:
    depth = 0
    for index in range(open_pos, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return index
    return -1


def parameter_arity(params: str) -> int:
    if not params.strip():
        return 0
    depth = 0
    count = 1
    for char in params:
        if char in "(<[{":
            depth += 1
        elif char in ")>]}":
            depth = max(0, depth - 1)
        elif char == "," and depth == 0:
            count += 1
    return count


def body_span(masked: str, close_paren: int) -> tuple[str, int, int]:
    index = close_paren + 1
    while index < len(masked):
        char = masked[index]
        if char == "{":
            depth = 0
            for end in range(index, len(masked)):
                if masked[end] == "{":
                    depth += 1
                elif masked[end] == "}":
                    depth -= 1
                    if depth == 0:
                        return "block", index, end + 1
            return "block", index, len(masked)
        if char == "=":
            start = index + 1
            end = start
            paren = brace = square = 0
            while end < len(masked):
                c = masked[end]
                if c == "(":
                    paren += 1
                elif c == ")" and paren:
                    paren -= 1
                elif c == "{":
                    brace += 1
                elif c == "}" and brace:
                    brace -= 1
                elif c == "[":
                    square += 1
                elif c == "]" and square:
                    square -= 1
                elif c == "\n" and paren == brace == square == 0:
                    next_index = end + 1
                    while next_index < len(masked) and masked[next_index] in " \t":
                        next_index += 1
                    if next_index < len(masked) and masked[next_index] in ".?:,":
                        end += 1
                        continue
                    return "expr", start, end
                end += 1
            return "expr", start, len(masked)
        if char == "\n":
            return "abstract", index, index
        index += 1
    return "abstract", index, index


def line_starts(text: str) -> list[int]:
    return [0] + [match.end() for match in re.finditer("\n", text)]


def line_number(starts: list[int], position: int) -> int:
    return bisect.bisect_right(starts, position)
