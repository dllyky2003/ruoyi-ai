#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Project Structure to Markdown Generator
递归遍历目录，生成项目结构的 Markdown 文档。
可选：为 Java 源码生成 Mermaid 类图。

用法:
    python project2md.py /path/to/project [-o output.md] [--include-content] [--max-size 1024] [-j]

示例:
    python project2md.py "D:\\CaveSystem\\Demos\\ruoyi-ai\\ruoyi-modules\\ruoyi-acls-engine\\src\\main" -o ruyi-acls-engine.md -j
    python project2md.py "D:\\CaveSystem\\Demos\\ruoyi-ai\\ruoyi-modules\\ruoyi-ai-ems\\src\\main" -o ruyi-ai-ems.md -j
    python project2md.py "D:\\CaveSystem\\Demos\\ruoyi-ai\\ruoyi-modules\\ruoyi-acls-tutor\\src\\main" -o ruyi-acls-tutor.md -j
"""
import os
import re
import sys
import argparse
from pathlib import Path
from datetime import datetime
from fnmatch import fnmatch

# 默认忽略的目录名（支持通配符）
IGNORE_DIRS = [
    ".git", "__pycache__", ".idea", ".vscode", "node_modules",
    "venv", ".venv", "env", ".env", "dist", "build", "*.egg-info",
    ".pytest_cache", ".mypy_cache", ".ruff_cache", ".coverage",
    "logs", "tmp", "temp", "out", "target"
]

# 忽略的文件（通配符）
IGNORE_FILES = [
    "*.pyc", "*.pyo", "*.so", "*.dll", "*.exe", "*.class",
    "*.log", "*.tmp", "*.swp", "*.swo", "*.bak",
    "*.zip", "*.tar.gz", "*.rar", "*.7z",
    "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.ico",
    "*.mp4", "*.avi", "*.mov", "*.mkv",
    "*.mp3", "*.wav", "*.flac",
    "*.pdf", "*.doc", "*.docx", "*.xls", "*.xlsx", "*.ppt", "*.pptx"
]

# 适合显示内容的文件扩展名（用于 --include-content）
CONTENT_EXTS = {
    ".py", ".js", ".ts", ".jsx", ".tsx", ".java", ".c", ".cpp", ".h",
    ".go", ".rs", ".rb", ".php", ".sh", ".bash", ".zsh", ".fish",
    ".md", ".rst", ".txt", ".log", ".json", ".yaml", ".yml", ".toml",
    ".ini", ".cfg", ".conf", ".xml", ".html", ".css", ".scss", ".less",
    ".sql", ".dockerfile", ".makefile", ".cmake", ".gradle"
}

# 无扩展名但需要作为文本处理的特殊文件
SPECIAL_TEXT_FILES = {"dockerfile", "makefile"}


# ================= 通用工具 =================

def should_ignore(path: Path, is_dir: bool, ignore_dirs, ignore_files):
    """判断是否应该忽略该路径。"""
    name = path.name
    if is_dir:
        for pattern in ignore_dirs:
            if fnmatch(name, pattern):
                return True
    else:
        for pattern in ignore_files:
            if fnmatch(name, pattern):
                return True
    return False


def format_size(size: int) -> str:
    """将字节数转换为人类可读格式。"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size < 1024.0:
            return f"{size:.1f} {unit}"
        size /= 1024.0
    return f"{size:.1f} TB"


def get_file_info(path: Path) -> dict:
    """获取文件元数据。"""
    stat = path.stat()
    return {
        "size": stat.st_size,
        "size_human": format_size(stat.st_size),
        "mtime": datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M:%S")
    }


def read_file_content(path: Path, max_size: int = 1024) -> str | None:
    """读取文件内容（仅限于文本文件且大小不超过 max_size KB）。"""
    if path.stat().st_size > max_size * 1024:
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()
    except (UnicodeDecodeError, PermissionError, OSError):
        return None


# ================= Java 解析（用于生成 Mermaid 类图） =================

JAVA_MODIFIERS = {
    "public", "protected", "private", "static", "final", "abstract",
    "synchronized", "native", "strictfp", "default", "transient",
    "volatile", "sealed", "non-sealed"
}

_ANNOTATION = r'@[\w.]+(?:\s*\([^()]*\))?'
_TYPE_MODS = (r'(?:(?:public|protected|private|abstract|final|static|'
              r'sealed|non-sealed|strictfp|' + _ANNOTATION + r')\s+)*')

_TYPE_DECL_RE = re.compile(
    r'(?P<mods>' + _TYPE_MODS + r')'
    r'(?P<kind>class|interface|enum|record)\s+'
    r'(?P<name>\w+)'
)


def _strip_comments(code: str) -> str:
    """粗略去掉注释与字符串字面量，避免干扰解析。"""
    code = re.sub(r'/\*.*?\*/', ' ', code, flags=re.DOTALL)
    code = re.sub(r'//[^\n]*', ' ', code)
    code = re.sub(r'"(?:\\.|[^"\\])*"', '""', code)
    code = re.sub(r"'(?:\\.|[^'\\])*'", "''", code)
    return code


def _strip_annotations(s: str) -> str:
    return re.sub(r'@[\w.]+\s*(?:\([^()]*\))?', ' ', s)


def _split_top_level(s: str, sep: str = ',') -> list:
    """按顶层分隔符切分，忽略 <> 与 () 内部的逗号。"""
    parts, buf, angle, paren = [], [], 0, 0
    for ch in s:
        if ch == '<':
            angle += 1
        elif ch == '>':
            angle -= 1
        elif ch == '(':
            paren += 1
        elif ch == ')':
            paren -= 1
        if ch == sep and angle == 0 and paren == 0:
            parts.append(''.join(buf).strip())
            buf = []
        else:
            buf.append(ch)
    tail = ''.join(buf).strip()
    if tail:
        parts.append(tail)
    return parts


def _scan_to_open_brace(code: str, start: int) -> int:
    """从 start 向后扫描到顶层 '{'，返回其索引（找不到返回 -1）。"""
    angle = 0
    for i in range(start, len(code)):
        ch = code[i]
        if ch == '<':
            angle += 1
        elif ch == '>':
            angle -= 1
        elif ch == '{' and angle == 0:
            return i
    return -1


def _find_matching_brace(code: str, start: int) -> int:
    depth = 0
    for i in range(start, len(code)):
        if code[i] == '{':
            depth += 1
        elif code[i] == '}':
            depth -= 1
            if depth == 0:
                return i
    return -1


def _parse_field(stmt: str):
    if '(' in stmt:                       # 带括号的更像方法
        return None
    stmt = _strip_annotations(stmt).strip()
    stmt = stmt.split('=')[0].strip()
    if not stmt:
        return None
    parts = stmt.split()
    mods = []
    while parts and parts[0] in JAVA_MODIFIERS:
        mods.append(parts.pop(0))
    if len(parts) < 2:
        return None
    return {"name": parts[-1], "type": ' '.join(parts[:-1]), "mods": mods}


def _parse_method(stmt: str, class_name: str):
    stmt = _strip_annotations(stmt).strip()
    m = re.match(
        r'^(?P<pre>.*?)\((?P<params>.*)\)\s*(?:throws\s+[\w.,\s<>]+)?$',
        stmt, re.DOTALL
    )
    if not m:
        return None
    pre = m.group('pre').strip()
    params = m.group('params').strip()
    tokens = pre.split()
    mods = []
    while tokens and tokens[0] in JAVA_MODIFIERS:
        mods.append(tokens.pop(0))
    if not tokens:
        return None
    name = tokens[-1]
    ret = ' '.join(tokens[:-1]) or None
    is_ctor = (name == class_name)
    return {
        "name": name, "return": None if is_ctor else ret,
        "params": params, "mods": mods, "ctor": is_ctor
    }


def _extract_members(body: str, class_name: str):
    fields, methods = [], []
    i, n, depth, start = 0, len(body), 0, 0
    while i < n:
        ch = body[i]
        if ch == '{':
            if depth == 0:
                stmt = body[start:i].strip()
                # 跳过嵌套类/接口/枚举（当它本身是一次类型声明时）
                if stmt and not _TYPE_DECL_RE.search(stmt):
                    mt = _parse_method(stmt, class_name)
                    if mt:
                        methods.append(mt)
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                start = i + 1
        elif ch == ';' and depth == 0:
            stmt = body[start:i].strip()
            if stmt:
                f = _parse_field(stmt)
                if f:
                    fields.append(f)
            start = i + 1
        i += 1
    return fields, methods


def _build_type(m, header: str, body: str) -> dict:
    kind, name = m.group('kind'), m.group('name')
    mods = m.group('mods').split()

    extends, implements = [], []
    em = re.search(r'\bextends\s+(.+?)(?=\s+implements\b|$)', header)
    if em:
        extends = _split_top_level(em.group(1))
    im = re.search(r'\bimplements\s+(.+)$', header)
    if im:
        implements = _split_top_level(im.group(1))

    fields, methods = _extract_members(body, name)
    return {
        "name": name, "kind": kind, "mods": mods,
        "extends": extends, "implements": implements,
        "fields": fields, "methods": methods
    }


def parse_java_source(code: str) -> list:
    """解析 Java 源码，返回其中的顶层类型列表。"""
    code = _strip_comments(code)
    types, pos = [], 0
    while True:
        m = _TYPE_DECL_RE.search(code, pos)
        if not m:
            break
        # 排除 Foo.class / new Xxx() 之类的误匹配
        if m.start() > 0 and (code[m.start() - 1].isalnum()
                              or code[m.start() - 1] in '._'):
            pos = m.end()
            continue
        brace = _scan_to_open_brace(code, m.end())
        if brace == -1:
            break
        end = _find_matching_brace(code, brace)
        if end == -1:
            break
        types.append(_build_type(m, code[m.end():brace], code[brace + 1:end]))
        pos = end + 1
    return types


def _visibility(mods) -> str:
    if 'public' in mods:
        return '+'
    if 'protected' in mods:
        return '#'
    if 'private' in mods:
        return '-'
    return '~'


def _clean_params(params: str) -> str:
    if not params:
        return ''
    out = []
    for p in _split_top_level(params):
        p = _strip_annotations(p).strip()
        toks = [t for t in p.split() if t not in JAVA_MODIFIERS]
        if len(toks) >= 2:
            out.append(f"{toks[-2]} {toks[-1]}")
        elif toks:
            out.append(toks[-1])
    return ', '.join(out)


def _base_name(t: str) -> str:
    t = re.sub(r'<.*', '', t.strip())      # 去掉泛型
    return t.split('.')[-1]                # 去掉包名


def java_to_mermaid(types: list) -> str:
    """将解析出来的类型列表渲染成 Mermaid classDiagram。"""
    lines = ['```mermaid', 'classDiagram']
    for t in types:
        lines.append(f'    class {t["name"]} {{')
        if t['kind'] == 'interface':
            lines.append('        <<interface>>')
        elif t['kind'] == 'enum':
            lines.append('        <<enumeration>>')
        for f in t['fields']:
            sfx = '$' if 'static' in f['mods'] else ''
            lines.append(
                f"        {_visibility(f['mods'])}{f['name']} : {f['type']}{sfx}"
            )
        for m in t['methods']:
            params = _clean_params(m['params'])
            if 'static' in m['mods']:
                sfx = '$'
            elif 'abstract' in m['mods']:
                sfx = '*'
            else:
                sfx = ''
            if m['ctor']:
                lines.append(
                    f"        {_visibility(m['mods'])}{m['name']}({params}){sfx}"
                )
            else:
                lines.append(
                    f"        {_visibility(m['mods'])}{m['name']}({params}) "
                    f"{m['return'] or 'void'}{sfx}"
                )
        lines.append('    }')

    # 继承 / 实现关系
    for t in types:
        for e in t['extends']:
            lines.append(f"    {_base_name(e)} <|-- {t['name']}")
        for im in t['implements']:
            lines.append(f"    {_base_name(im)} <|.. {t['name']}")

    lines.append('```')
    return '\n'.join(lines)


# ================= 主逻辑：生成目录树 =================

def generate_tree(
    root_dir: Path,
    output_file,
    include_content: bool = False,
    max_size_kb: int = 1024,
    ignore_dirs=None,
    ignore_files=None,
    java_diagram: bool = False,
    prefix: str = "",
    depth: int = 0
):
    """递归生成目录树并写入 Markdown。"""
    if ignore_dirs is None:
        ignore_dirs = IGNORE_DIRS
    if ignore_files is None:
        ignore_files = IGNORE_FILES

    # 根目录头部信息（只写一次）
    if depth == 0:
        output_file.write(f"# 📁 项目结构：{root_dir.name}\n\n")
        output_file.write(f"**根路径**: `{root_dir.resolve()}`\n\n")
        output_file.write(
            f"**生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n"
        )

    # 收集当前目录下的子项
    try:
        items = list(root_dir.iterdir())
    except (PermissionError, OSError):
        output_file.write(f"{prefix}└── ⚠️ *(无法访问该目录)*\n")
        return

    dirs = sorted(
        [p for p in items if p.is_dir()
         and not should_ignore(p, True, ignore_dirs, ignore_files)],
        key=lambda p: p.name.lower()
    )
    files = sorted(
        [p for p in items if p.is_file()
         and not should_ignore(p, False, ignore_dirs, ignore_files)],
        key=lambda p: p.name.lower()
    )

    # 目录在前、文件在后，统一作为「条目」处理，保证 is_last 判断一致
    entries = [(d, True) for d in dirs] + [(f, False) for f in files]

    for idx, (entry, is_dir) in enumerate(entries):
        is_last = (idx == len(entries) - 1)
        connector = "└── " if is_last else "├── "
        # 子项使用的前缀：最后一项下面不画竖线，否则补一条竖线
        child_prefix = prefix + ("    " if is_last else "│   ")

        if is_dir:
            output_file.write(f"{prefix}{connector}📁 **{entry.name}**\n")
            generate_tree(
                entry, output_file,
                include_content=include_content,
                max_size_kb=max_size_kb,
                ignore_dirs=ignore_dirs,
                ignore_files=ignore_files,
                java_diagram=java_diagram,
                prefix=child_prefix,
                depth=depth + 1
            )
        else:
            info = get_file_info(entry)
            output_file.write(
                f"{prefix}{connector}📄 **{entry.name}**  "
                f"({info['size_human']}, {info['mtime']})\n"
            )

            # —— Java 类图 ——
            if java_diagram and entry.suffix.lower() == '.java':
                try:
                    src = entry.read_text(encoding='utf-8')
                except (UnicodeDecodeError, OSError):
                    src = None
                if src:
                    types = parse_java_source(src)
                    if types:
                        output_file.write('\n')
                        output_file.write(java_to_mermaid(types))
                        output_file.write('\n\n')

            # —— 可选：嵌入内容 ——
            if include_content:
                suffix = entry.suffix.lower()
                lower_name = entry.name.lower()
                is_text_candidate = (suffix in CONTENT_EXTS) or (
                    lower_name in SPECIAL_TEXT_FILES
                )

                if is_text_candidate:
                    content = read_file_content(entry, max_size_kb)
                    if content is not None:
                        # 推断代码块语言
                        if lower_name in SPECIAL_TEXT_FILES:
                            lang = lower_name
                        else:
                            lang = suffix[1:] if suffix else ""
                        output_file.write(f"\n```{lang}\n")
                        output_file.write(content.rstrip() + "\n")
                        output_file.write("```\n\n")
                    else:
                        output_file.write(
                            f"{prefix}    *(内容过大或无法读取，已跳过)*\n"
                        )


def main():
    parser = argparse.ArgumentParser(description="生成项目结构的 Markdown 文档。")
    parser.add_argument("path", help="要扫描的项目根路径")
    parser.add_argument(
        "-o", "--output", default="project_structure.md",
        help="输出的 Markdown 文件路径"
    )
    parser.add_argument(
        "-c", "--include-content", action="store_true",
        help="包含小文本文件的内容（以代码块形式）"
    )
    parser.add_argument(
        "--max-size", type=int, default=1024,
        help="嵌入内容的文件大小上限（KB），默认 1024 KB"
    )
    parser.add_argument(
        "-j", "--java-class-diagram", action="store_true",
        help="为 Java 文件生成 Mermaid 类图"
    )
    parser.add_argument(
        "--ignore-dirs", nargs="*", default=[],
        help="额外忽略的目录名（支持通配符）"
    )
    parser.add_argument(
        "--ignore-files", nargs="*", default=[],
        help="额外忽略的文件名（支持通配符）"
    )
    args = parser.parse_args()

    root = Path(args.path).resolve()
    if not root.exists():
        print(f"错误：路径 '{root}' 不存在。", file=sys.stderr)
        sys.exit(1)
    if not root.is_dir():
        print(f"错误：路径 '{root}' 不是一个目录。", file=sys.stderr)
        sys.exit(1)

    # 合并忽略规则
    ignore_dirs = IGNORE_DIRS + args.ignore_dirs
    ignore_files = IGNORE_FILES + args.ignore_files

    with open(args.output, "w", encoding="utf-8") as out:
        generate_tree(
            root, out,
            include_content=args.include_content,
            max_size_kb=args.max_size,
            ignore_dirs=ignore_dirs,
            ignore_files=ignore_files,
            java_diagram=args.java_class_diagram
        )

    print(f"✅ 项目结构已生成：{args.output}")


if __name__ == "__main__":
    main()
