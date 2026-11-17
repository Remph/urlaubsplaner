#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Remph
"""Rebuilds data/school-holidays/school-holidays.txt from the OpenHolidays API.

Usage: python3 tools/update-school-holidays.py FIRST_YEAR LAST_YEAR

Needs only the Python standard library and network access to openholidaysapi.org. Run it once a
year, read the diff, then cross-check against the KMK table (see data/school-holidays/README.md).
"""
import collections
import datetime
import json
import pathlib
import sys
import urllib.request

API = "https://openholidaysapi.org/SchoolHolidays"
OUT = pathlib.Path(__file__).resolve().parent.parent / "data" / "school-holidays" / "school-holidays.txt"
LAENDER = {"BB", "BE", "BW", "BY", "HB", "HE", "HH", "MV", "NI", "NW", "RP", "SH", "SL", "SN", "ST", "TH"}
# Mecklenburg-Vorpommern lists general (ABS) and vocational (BBS) schools separately; the app
# shows the general schools. An entry for both groups counts as general.
GENERAL_SCHOOLS = "DE-MV-ABS"
KNOWN_GROUPS = {"DE-MV-ABS", "DE-MV-BBS"}

HEADER = """\
# School holidays (Schulferien) per Bundesland, Germany.
# Contains information from OpenHolidays API (https://www.openholidaysapi.org), which is made
# available here under the Open Database License (ODbL). Licence text: ODbL-1.0.md. This file
# is a derivative database and stays under the ODbL; see README.md for what was left out.
# Rebuilt by tools/update-school-holidays.py. Do not edit by hand.
# Lines: land;from;to;name_de;name_en (dates inclusive, ISO format)
format: 1
version: {version}
covers: {first}..{last}
"""


def fetch(year):
    url = f"{API}?countryIsoCode=DE&validFrom={year}-01-01&validTo={year}-12-31"
    request = urllib.request.Request(url, headers={"accept": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def main(first_year, last_year):
    entries = {}
    for year in range(first_year, last_year + 1):
        for entry in fetch(year):
            entries[entry["id"]] = entry

    names_en = collections.defaultdict(collections.Counter)
    rows = set()
    skipped = collections.Counter()
    for entry in entries.values():
        if entry["type"] != "School" or entry["nationwide"]:
            raise SystemExit(f"unexpected entry, check by hand: {entry}")
        if "Exception" in entry.get("tags", []):
            skipped["exception (for example the Schleswig-Holstein islands)"] += 1
            continue
        groups = {g["code"] for g in entry.get("groups", [])}
        if not groups <= KNOWN_GROUPS:
            raise SystemExit(f"unknown group, decide by hand: {groups}")
        if groups and GENERAL_SCHOOLS not in groups:
            skipped["vocational schools only"] += 1
            continue
        if len(entry["subdivisions"]) != 1:
            raise SystemExit(f"entry for several subdivisions, check by hand: {entry}")
        land = entry["subdivisions"][0]["shortName"]
        if land not in LAENDER:
            raise SystemExit(f"unknown land {land}")
        by_language = {n["language"]: n["text"].strip() for n in entry["name"]}
        name_de, name_en = by_language["DE"], by_language["EN"]
        if ";" in name_de + name_en:
            raise SystemExit(f"semicolon in a name: {entry}")
        names_en[name_de][name_en] += 1
        rows.add((land, entry["startDate"], entry["endDate"], name_de))

    # The API spells the English names inconsistently ("Summer holidays" and "Summer Holidays");
    # use the most frequent spelling per German name, the lower-case one on a tie.
    english = {
        de: sorted(counts.items(), key=lambda kv: (-kv[1], kv[0] != kv[0].capitalize(), kv[0]))[0][0]
        for de, counts in names_en.items()
    }
    body = "".join(
        f"{land};{start};{end};{de};{english[de]}\n" for land, start, end, de in sorted(rows)
    )
    missing = LAENDER - {r[0] for r in rows}
    if missing:
        raise SystemExit(f"no data for {sorted(missing)}")
    OUT.write_text(
        HEADER.format(version=datetime.date.today(), first=f"{first_year}-01-01", last=f"{last_year}-12-31")
        + body,
        encoding="utf-8",
        newline="\n",
    )
    print(f"{len(rows)} rows written to {OUT}; left out: {dict(skipped)}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(int(sys.argv[1]), int(sys.argv[2]))
