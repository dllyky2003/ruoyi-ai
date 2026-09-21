#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Project Structure to Markdown Generator
递归遍历目录，生成项目结构的 Markdown 文档。
用法: python project2md.py /path/to/project [-o output.md] [--include-content] [--max-size 1024]


python project2md.py "D:\CaveSystem\Demos\ruoyi-ai\ruoyi-modules\ruoyi-acls-engine\src\main" -o ruyi-acls-engine.md
python project2md.py "D:\CaveSystem\Demos\ruoyi-ai\ruoyi-modules\ruoyi-ai-ems\src\main" -o ruyi-ai-ems.md
python project2md.py "D:\CaveSystem\Demos\ruoyi-ai\ruoyi-modules\ruoyi-acls-tutor\src\main" -o ruyi-acls-tutor.md
"""
import os
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


def generate_tree(
    root_dir: Path,
    output_file,
    include_content: bool = False,
    max_size_kb: int = 1024,
    ignore_dirs=None,
    ignore_files=None,
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
        output_file.write(f"**生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")

    # 收集当前目录下的子项
    try:
        items = list(root_dir.iterdir())
    except (PermissionError, OSError):
        output_file.write(f"{prefix}└── ⚠️ *(无法访问该目录)*\n")
        return

    dirs = sorted(
        [p for p in items if p.is_dir() and not should_ignore(p, True, ignore_dirs, ignore_files)],
        key=lambda p: p.name.lower()
    )
    files = sorted(
        [p for p in items if p.is_file() and not should_ignore(p, False, ignore_dirs, ignore_files)],
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
                prefix=child_prefix,
                depth=depth + 1
            )
        else:
            info = get_file_info(entry)
            output_file.write(
                f"{prefix}{connector}📄 **{entry.name}**  ({info['size_human']}, {info['mtime']})\n"
            )

            # 可选：嵌入内容
            if include_content:
                suffix = entry.suffix.lower()
                lower_name = entry.name.lower()
                is_text_candidate = (suffix in CONTENT_EXTS) or (lower_name in SPECIAL_TEXT_FILES)

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
                        output_file.write(f"{prefix}    *(内容过大或无法读取，已跳过)*\n")


def main():
    parser = argparse.ArgumentParser(description="生成项目结构的 Markdown 文档。")
    parser.add_argument("path", help="要扫描的项目根路径")
    parser.add_argument("-o", "--output", default="project_structure.md", help="输出的 Markdown 文件路径")
    parser.add_argument("-c", "--include-content", action="store_true", help="包含小文本文件的内容（以代码块形式）")
    parser.add_argument("--max-size", type=int, default=1024, help="嵌入内容的文件大小上限（KB），默认 1024 KB")
    parser.add_argument("--ignore-dirs", nargs="*", default=[], help="额外忽略的目录名（支持通配符）")
    parser.add_argument("--ignore-files", nargs="*", default=[], help="额外忽略的文件名（支持通配符）")
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
            ignore_files=ignore_files
        )

    print(f"✅ 项目结构已生成：{args.output}")


if __name__ == "__main__":
    main()
