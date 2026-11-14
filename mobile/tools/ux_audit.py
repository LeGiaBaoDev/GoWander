#!/usr/bin/env python3
"""UX audit of the screen currently shown on the Android emulator (uiautomator dump), stdlib only.

    python3 mobile/tools/ux_audit.py "Tên màn"          # prints a markdown section, exit 1 on any problem
    python3 mobile/tools/ux_audit.py --self-test

Checks (WCAG 2.2 + Material/Apple sizes, see docs/testing/ux-checklist.md):
  T1 every clickable element is at least 48x48 dp
  T2 every clickable element has a name (its text, a child's text, or a content description)
  T3 clickable elements do not overlap each other (a tap could hit the wrong one)
  T4 nothing clickable sits under the status bar
  T0 the screen has clickable elements of the app at all (guards against a silent pass)
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
MIN_DP = 48
PACKAGE = "com.gowander.app"  # applicationId (the Kotlin package is com.gowander.android)


def bounds(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]")))
    return x1, y1, x2, y2


def name_of(node):
    if node.get("text") or node.get("content-desc"):
        return node.get("text") or node.get("content-desc")
    for child in node.iter("node"):
        if child.get("text") or child.get("content-desc"):
            return child.get("text") or child.get("content-desc")
    return ""


def audit(root, density, status_bar_px):
    """List of (check, element name, detail) problems."""
    px_min = MIN_DP * density / 160
    parent = {c: p for p in root.iter("node") for c in p}

    def clipped(n):
        """Cut by the edge of a scrolling list (half scrolled out): its full size is not visible, so T1 cannot judge it."""
        a = parent.get(n)
        while a is not None and a.get("scrollable") != "true":
            a = parent.get(a)
        if a is None:
            return False
        _, ny1, _, ny2 = bounds(n)
        _, ay1, _, ay2 = bounds(a)
        return ny1 <= ay1 + 2 or ny2 >= ay2 - 2
    clickables = [n for n in root.iter("node") if n.get("clickable") == "true" and n.get("enabled", "true") == "true"
                  and n.get("package", PACKAGE) == PACKAGE]  # only GoWander, not the launcher or system UI
    problems = []
    boxes = []
    for n in clickables:
        x1, y1, x2, y2 = bounds(n)
        w, h = x2 - x1, y2 - y1
        if w <= 0 or h <= 0:
            continue  # off screen (scrolled away)
        label = name_of(n)
        if (w + 1 < px_min or h + 1 < px_min) and not clipped(n):
            problems.append(("T1", label or "?", f"{w * 160 / density:.0f}x{h * 160 / density:.0f} dp"))
        if not label:
            problems.append(("T2", f"[{x1},{y1}]", "no text or content description"))
        if y1 < status_bar_px:
            problems.append(("T4", label or "?", f"top {y1}px < status bar {status_bar_px}px"))
        boxes.append((x1, y1, x2, y2, label))
    if not clickables:
        problems.append(("T0", "?", "no clickable element of the app found (wrong package or screen?)"))  # never pass on nothing
    for i, a in enumerate(boxes):
        for b in boxes[i + 1:]:
            # nested (a card that contains its own button) is allowed; partial overlap is not
            nested = (a[0] <= b[0] and a[1] <= b[1] and a[2] >= b[2] and a[3] >= b[3]) or (b[0] <= a[0] and b[1] <= a[1] and b[2] >= a[2] and b[3] >= a[3])
            ox = min(a[2], b[2]) - max(a[0], b[0])
            oy = min(a[3], b[3]) - max(a[1], b[1])
            if ox > 2 and oy > 2 and not nested:
                problems.append(("T3", f"{a[4] or '?'} / {b[4] or '?'}", f"overlap {ox}x{oy}px"))
    return problems, len(clickables)


def dump():
    for _ in range(4):
        subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ux.xml"], capture_output=True)
        xml = subprocess.run([ADB, "shell", "cat", "/sdcard/ux.xml"], capture_output=True, text=True).stdout
        try:
            root = ET.fromstring(xml)
            if any(n.get("text") for n in root.iter("node")):
                return root
        except ET.ParseError:
            pass
        time.sleep(1.5)
    sys.exit("uiautomator returned no tree")


def self_test():
    xml = """<hierarchy><node clickable="false" bounds="[0,0][1080,2400]">
      <node clickable="true" text="OK" bounds="[0,200][200,400]"/>
      <node clickable="true" text="" content-desc="" bounds="[300,200][500,400]"/>
      <node clickable="true" text="Nhỏ" bounds="[600,200][640,240]"/>
      <node clickable="true" text="A" bounds="[0,500][300,700]"/><node clickable="true" text="B" bounds="[200,600][500,800]"/>
      <node clickable="true" text="Card" bounds="[0,900][1000,1300]"><node clickable="true" text="Inner" bounds="[100,1000][400,1200]"/></node>
      <node clickable="true" text="Top" bounds="[0,10][200,200]"/>
    </node></hierarchy>"""
    problems, n = audit(ET.fromstring(xml), density=420, status_bar_px=60)
    kinds = sorted((p[0], p[1]) for p in problems)
    assert ("T1", "Nhỏ") in kinds and ("T2", "[300,200]") in kinds and ("T3", "A / B") in kinds and ("T4", "Top") in kinds, kinds
    assert not any(p[0] == "T3" and "Inner" in p[1] for p in problems), "nested must not count as overlap"
    assert not any(p[1] == "OK" for p in problems)
    scroll = """<hierarchy><node scrollable="true" clickable="false" bounds="[0,0][1080,1000]">
      <node clickable="true" text="Half" bounds="[0,960][1080,1000]"/><node clickable="true" text="Tiny" bounds="[0,400][40,440]"/></node></hierarchy>"""
    p2, _ = audit(ET.fromstring(scroll), 420, 0)
    assert [(p[0], p[1]) for p in p2] == [("T1", "Tiny")], p2  # a row cut by the list edge is not a T1 failure
    empty, _ = audit(ET.fromstring("<hierarchy><node clickable='false' bounds='[0,0][10,10]'/></hierarchy>"), 420, 60)
    assert [p[0] for p in empty] == ["T0"], empty
    print("self-test ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    screen = sys.argv[1] if len(sys.argv) > 1 else "screen"
    density = int(re.findall(r"\d+", subprocess.run([ADB, "shell", "wm", "density"], capture_output=True, text=True).stdout)[-1])
    status_bar_px = round(24 * density / 160)
    problems, n = audit(dump(), density, status_bar_px)
    print(f"### {screen}\n\n{n} phần tử bấm được · {'**ĐẠT**' if not problems else f'**{len(problems)} vấn đề**'}\n")
    for check, who, detail in problems:
        print(f"- {check} `{who}`: {detail}")
    print()
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
