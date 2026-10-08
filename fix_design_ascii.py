from pathlib import Path
p = Path(r"F:\PROJECTS\git\distributed-order-system\design.md")
s = p.read_text(encoding="utf-8")
replacements = {
    "â€™": "'",
    "â€\u009D": '"',
    "â€œ": '"',
    "â€”": "-",
    "â€“": "-",
    "â€": '"',
    "’": "'",
    "“": '"',
    "”": '"',
    "—": "-",
    "–": "-",
}
for a, b in replacements.items():
    s = s.replace(a, b)
p.write_text(s, encoding="utf-8", newline="\r\n")
print("ASCII cleanup complete")
