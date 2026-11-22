#!/usr/bin/env python3
"""Validate translated resources and the locale list generated from their directories."""
import argparse
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
LOCALES = ('ar', 'es', 'ru', 'zh-rCN')


def resources(path):
    nodes = [n for file in path.glob("*.xml") for n in ET.parse(file).getroot()
             if n.tag in ("string", "string-array", "plurals")]
    keys = [(n.tag, n.attrib['name']) for n in nodes]
    assert len(keys) == len(set(keys)), f'Duplicate resources in {path}'
    return {(n.tag, n.attrib['name']): n for n in nodes}


def placeholders(text):
    # Preserve argument position and type; Android allows implicit position for a single argument.
    # A literal %% must be consumed as a pair even when followed by a letter such as in "%% de".
    matches = re.findall(r'%%|%(?:(\d+)\$)?[\-+0,(#]*\d*(?:\.\d+)?([sdf])', text)
    arguments = [(position, kind) for position, kind in matches if kind]
    return sorted((int(pos) if pos else i + 1, kind) for i, (pos, kind) in enumerate(arguments))


def check_formats(source, translation, locale, key):
    if source.get("formatted") == "false":
        return
    assert placeholders("".join(source.itertext())) == placeholders(
        "".join(translation.itertext())
    ), (locale, key, "format arguments")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="Regenerate Android's locale list")
    args = parser.parse_args()
    directories = sorted(p.parent for p in RES.glob("values-*/strings.xml"))
    base = resources(RES / "values")
    required = {key: node for key, node in base.items() if node.get("translatable") != "false"}
    for directory in directories:
        locale = directory.name.removeprefix("values-")
        translated = resources(directory)
        if locale in LOCALES:
            missing = required.keys() - translated.keys()
            assert not missing, (locale, "missing", sorted(missing))
        for key in required.keys() & translated.keys():
            node, other = required[key], translated[key]
            if node.tag == "string":
                check_formats(node, other, locale, key)
            elif node.tag == "string-array":
                assert len(node) == len(other), (locale, key, "array size")
                for item, translated_item in zip(node, other):
                    check_formats(item, translated_item, locale, key)
            else:
                quantities = {item.get("quantity"): item for item in node}
                for item in other:
                    source = quantities.get(item.get("quantity"), quantities["other"])
                    check_formats(source, item, locale, key)
        print(f"{locale}: {len(required.keys() & translated.keys())}/{len(required)} translations checked")

    locales = {"en"} | {p.name.removeprefix("values-").replace("-r", "-") for p in directories}
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">']
    lines += [f'    <locale android:name="{locale}" />' for locale in sorted(locales)]
    expected = "\n".join([*lines, "</locale-config>", ""])
    config = RES / "xml/locales_config.xml"
    if args.write:
        config.write_text(expected, encoding="utf-8", newline="\n")
    elif config.read_text(encoding="utf-8-sig") != expected:
        raise SystemExit("Stale locale list. Run python3 scripts/check_localization.py --write")
    print("Generated system language list matches the translation directories")


if __name__ == "__main__":
    main()
