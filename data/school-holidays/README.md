# School holiday data (Schulferien)

`school-holidays.txt` lists the school holidays of the 16 Bundesländer for 2026 to 2029. The app
bundles this directory as assets and reads the file with `SchoolHolidayData` in `:core`; the
file is never fetched at runtime.

## Source and licence

Contains information from OpenHolidays API (https://www.openholidaysapi.org), which is made
available here under the Open Database License (ODbL).

The file is a derivative database of the OpenHolidays data (a reformatted, filtered extract), so it
stays under the ODbL 1.0: the full text is in `ODbL-1.0.md`. The licence applies to the data in
this directory only; the app's source code is GPL-3.0-or-later (see `LICENSE` in the repo root). Anything
added to the file by hand must be ODbL compatible. Notes on the licence: `docs/data-sources.md`.

## What is in the file

Line format: `land;from;to;name_de;name_en`. Land is the two-letter code (`BY`), dates are ISO and
inclusive, a single school-free day has the same `from` and `to`. The first lines say the format
version (`format`), the export day (`version`) and the dates the file has data for (`covers`).
Outside `covers` the app must say "no data", not "no holidays".

Left out on purpose:

- Entries tagged `Exception` by OpenHolidays: the Schleswig-Holstein islands (Sylt, Föhr, Amrum,
  Helgoland, Halligen), which have their own summer and autumn dates.
- Vocational schools (BBS) in Mecklenburg-Vorpommern, which has separate dates. The general
  schools (ABS) are used.
- Holidays of single cities or communities (the API has none for school holidays in these years).

English names: the API spells them inconsistently ("Summer holidays", "Summer Holidays"); the
update script uses the most frequent spelling per German name.

## Yearly update

1. `python3 tools/update-school-holidays.py FIRST_YEAR LAST_YEAR` (standard library only; needs
   openholidaysapi.org). It rewrites `school-holidays.txt` and stops on anything it does not
   expect (a nationwide entry, an unknown group, a semicolon in a name).
2. Read the diff. Keep or move `covers` so it only claims years where all 16 Länder are complete
   (the API had 2030 only in part on 2026-10-25).
3. Cross-check against the Kultusministerkonferenz (https://www.kmk.org/service/ferienregelung.html):
   the long-term summer table and the yearly Ferienkalender PDFs, then the Länder ministries for
   anything that differs. `BundledSchoolHolidaysTest` has the spot checks; add the new years.
4. Run the checks from `CLAUDE.md`. The tests fail when a Land or year is missing.

## Cross-check log

- 2026-10-25, export of 2026 to 2029: summer holidays of all 16 Länder compared with the KMK table
  "Sommerferienregelung 2025 bis 2030": 92 of 96 identical. The four differences (Hamburg 2028 and
  2029 start on a weekend day in the KMK table; Schleswig-Holstein 2028 ends on a Saturday and 2029
  on 8 August) are covered by the KMK yearly PDFs, which agree with the file (Hamburg starts on
  the Monday; Schleswig-Holstein 2029 ends on 3 August). All 275 date ranges on a Land's own line
  in the yearly Ferienkalender PDFs 2025/26 to 2028/29 that fall inside the covered period match
  the file. Not machine-checked: ranges split over several lines in the PDFs (Ostern, Pfingsten,
  single days) and the Länder ministry sites, which the cloud container cannot open.
