"""Tests for scripts/build_bus_gtfs_from_osm.py (ADR-032). Run: python3 -m unittest discover -s scripts/tests"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import build_bus_gtfs_from_osm as b  # noqa: E402

# Tiny OPL extract: one route in two directions, one with no interval, one with too few stops inside the extract,
# one with single departures on weekdays, one with no ways (no shape: stop order cannot be checked). Stops are about 547 m apart along w10/w11.
OPL = """\
n1 v1 x106.6980 y10.7720 Tname=Tr%1ea1%m%20%A,highway=bus_stop,public_transport=platform
n2 v1 x106.7030 y10.7720 Tname=Tr%1ea1%m%20%B,highway=bus_stop,public_transport=platform
n3 v1 x106.7080 y10.7720 Thighway=bus_stop
n11 v1 x106.6980 y10.7721
n12 v1 x106.7030 y10.7721
n13 v1 x106.7080 y10.7721
w10 v1 Thighway=primary Nn11,n12
w11 v1 Thighway=primary Nn12,n13
r100 v1 Ttype=route,route=bus,ref=19,name=Tuy%1ebf%n%20%19,from=A,to=C,network=Xe%20%buýt%20%TP,operator=Công%20%ty%20%X,colour=#008080,interval=00:10-00:20,opening_hours=Mo-Su%20%05:00-22:00,public_transport:version=2 Mn1@platform_entry_only,n2@platform,n3@platform_exit_only,w10@,w11@
r101 v1 Ttype=route,route=bus,ref=19,name=Tuy%1ebf%n%20%19%20%v%1ec1%,network=Xe%20%buýt%20%TP,operator=Công%20%ty%20%X,interval=00:15,opening_hours=Mo-Fr%20%06:00-20:00;%20%Sa-Su%20%07:00-20:00,duration=00:10 Mn3@platform,n2@platform,n1@platform,w11@,w10@
r102 v1 Ttype=route,route=bus,ref=20,network=Xe%20%buýt%20%TP,operator=Y,opening_hours=Mo-Su%20%05:00-22:00 Mn1@platform,n2@platform
r103 v1 Ttype=route,route=bus,ref=21,network=Xe%20%buýt%20%TP,operator=Y,interval=00:10,opening_hours=Mo-Su%20%05:00-22:00 Mn1@platform,n999@platform
r104 v1 Ttype=route,route=bus,ref=22,network=Xe%20%buýt%20%TP,operator=Y,opening_hours=Mo-Fr%20%07:00%2c%17:15 Mn1@platform,n3@platform,w10@,w11@
r105 v1 Ttype=route,route=bus,ref=23,network=Xe%20%buýt%20%TP,operator=Y,interval=00:10,opening_hours=Mo-Su%20%05:00-22:00 Mn1@platform,n3@platform
"""


def hms_to_s(t):
    h, m, s = map(int, t.split(":"))
    return h * 3600 + m * 60 + s


class Parsing(unittest.TestCase):
    def test_opl_decoding(self):
        self.assertEqual(b.decode("Tr%1ea1%m%20%A"), "Trạm A")
        self.assertEqual(b.decode("07:00%2c%17:15"), "07:00,17:15")
        self.assertEqual(b.decode("plain"), "plain")

    def test_parse_opl(self):
        nodes, ways, rels = b.parse_opl(OPL.splitlines())
        self.assertEqual(nodes[1][:2], (10.7720, 106.6980))
        self.assertEqual(nodes[1][2]["name"], "Trạm A")
        self.assertEqual(ways[10], [11, 12])
        r = rels[0]
        self.assertEqual(r[0], 100)
        self.assertEqual(r[1]["operator"], "Công ty X")
        self.assertEqual(r[2][0], ("n", 1, "platform_entry_only"))
        self.assertEqual(r[2][3], ("w", 10, ""))

    def test_interval(self):
        self.assertEqual(b.parse_interval("00:10-00:20"), 900)
        self.assertEqual(b.parse_interval("00:15"), 900)
        self.assertEqual(b.parse_interval("01:00"), 3600)
        self.assertEqual(b.parse_interval("00:50-00:60"), 3300)  # "00:60" is used for 60 min in OSM HCMC
        for bad in (None, "", "often", "00:00", "10"):
            self.assertIsNone(b.parse_interval(bad), bad)

    def test_opening_hours(self):
        every_day = (1, 1, 1, 1, 1, 1, 1)
        self.assertEqual(b.parse_opening_hours("Mo-Su 05:00-22:00"), [(every_day, [(18000, 79200)], [])])
        self.assertEqual(b.parse_opening_hours("Mo-SU 04:50-19:10"), [(every_day, [(17400, 69000)], [])])
        self.assertEqual(
            b.parse_opening_hours("Mo-Fr 05:55-18:30; Sa 06:20-18:30; Su 07:20-18:30"),
            [((1, 1, 1, 1, 1, 0, 0), [(21300, 66600)], []), ((0, 0, 0, 0, 0, 1, 0), [(22800, 66600)], []),
             ((0, 0, 0, 0, 0, 0, 1), [(26400, 66600)], [])])
        self.assertEqual(b.parse_opening_hours("Mo-Fr 07:00,17:15"), [((1, 1, 1, 1, 1, 0, 0), [], [25200, 62100])])
        self.assertEqual(b.parse_opening_hours("Mo-Fr 16:00-16:10; Sa 11:20"),
                         [((1, 1, 1, 1, 1, 0, 0), [(57600, 58200)], []), ((0, 0, 0, 0, 0, 1, 0), [], [40800])])
        for bad in (None, "", "24/7", "Mo-Su sunrise-sunset", "Mo-Su 22:00-05:00", "PH off"):
            self.assertIsNone(b.parse_opening_hours(bad), bad)


class Geometry(unittest.TestCase):
    def setUp(self):
        self.nodes, self.ways, _ = b.parse_opl(OPL.splitlines())

    def test_shape_joins_and_flips_ways(self):
        fwd = b.assemble_shape([10, 11], self.ways, self.nodes)
        back = b.assemble_shape([11, 10], self.ways, self.nodes)
        self.assertEqual([p[1] for p in fwd], [106.6980, 106.7030, 106.7080])
        self.assertEqual([p[1] for p in back], [106.7080, 106.7030, 106.6980])

    def test_shape_with_gap_is_rejected(self):
        nodes = dict(self.nodes)
        nodes[14] = (10.7721, 106.7200, {})  # 1.3 km east of n13, no shared node
        nodes[15] = (10.7721, 106.7250, {})
        self.assertIsNone(b.assemble_shape([10, 11, 2], {**self.ways, 2: [14, 15]}, nodes))

    def test_shape_out_of_order_ways_chain_from_first_stop(self):
        ways = {**self.ways, 12: [13, 16], 13: [16, 17]}
        nodes = {**self.nodes, 16: (10.7721, 106.7100, {}), 17: (10.7721, 106.7120, {})}
        order = [12, 10, 13, 11]  # listed out of order, as in many HCMC relations
        self.assertIsNone(b.assemble_shape(order, ways, nodes))
        line = b.assemble_shape(order, ways, nodes, self.nodes[1][:2])
        self.assertEqual([p[1] for p in line], [106.6980, 106.7030, 106.7080, 106.7100, 106.7120])
        self.assertIsNone(b.assemble_shape([10, 11, 2], {**ways, 2: [14, 15]},
                                           {**nodes, 14: (10.7721, 106.7300, {}), 15: (10.7721, 106.7350, {})},
                                           self.nodes[1][:2]))  # a real hole stays rejected

    def test_shape_skips_missing_nodes_and_ways(self):
        self.assertIsNone(b.assemble_shape([999], self.ways, self.nodes))
        self.assertEqual(len(b.assemble_shape([10, 999, 11], self.ways, self.nodes)), 3)

    def test_stops_sorted_along_shape_and_zigzag_rejected(self):
        shape = b.assemble_shape([10, 11], self.ways, self.nodes)
        self.assertEqual(b.order_stops([1, 3, 2], self.nodes, shape), (shape, [1, 2, 3]))
        back, order = b.order_stops([3, 1, 2], self.nodes, shape)  # listed from the other end
        self.assertEqual((back, order), (shape[::-1], [3, 2, 1]))
        far = {**self.nodes, 9: (10.7900, 106.7030, {})}  # 2 km off the road: dropped
        self.assertEqual(b.order_stops([1, 9, 3], far, shape)[1], [1, 3])
        self.assertEqual(b.order_stops([1, 2, 3], self.nodes, None), (None, []))
        self.assertEqual(b.order_stops([9, 9], far, shape), (None, []))

    def test_offsets_follow_shape_and_speed(self):
        shape = b.assemble_shape([10, 11], self.ways, self.nodes)
        stops = [self.nodes[i][:2] for i in (1, 2, 3)]
        offs = b.stop_offsets(stops, shape, None)
        self.assertEqual(offs[0], 0)
        # about 1094 m at 15 km/h -> ~263 s; the middle stop at about half
        self.assertAlmostEqual(offs[2], 263, delta=5)
        self.assertAlmostEqual(offs[1], offs[2] / 2, delta=3)
        self.assertEqual(b.stop_offsets(stops, shape, 600)[2], 600)  # duration tag wins
        same_place = b.stop_offsets([stops[0], stops[0]], shape, None)
        self.assertGreater(same_place[1], 0)  # times always increase


class Build(unittest.TestCase):
    def setUp(self):
        self.feed, self.skipped = b.build(*b.parse_opl(OPL.splitlines()))

    def test_skips_are_reported_with_reasons(self):
        self.assertEqual(self.skipped, [(102, "20", "no_interval"), (103, "21", "too_few_stops"), (105, "23", "no_shape")])

    def test_routes_group_directions_by_network_and_ref(self):
        routes = {r[0]: r for r in self.feed["routes.txt"]}
        self.assertEqual(sorted(routes), ["xe-buyt-tp-19", "xe-buyt-tp-22"])
        r19 = routes["xe-buyt-tp-19"]
        self.assertEqual((r19[2], r19[5], r19[6]), ("19", 3, "008080"))
        self.assertIn(b.LABEL, r19[4])

    def test_trips_services_and_frequencies(self):
        trips = {t[2]: t for t in self.feed["trips.txt"]}
        self.assertEqual(sorted(trips), ["r100-D1111111", "r101-D0000011", "r101-D1111100",
                                         "r104-D1111100-0700", "r104-D1111100-1715"])
        freq = {f[0]: f for f in self.feed["frequencies.txt"]}
        self.assertEqual(freq["r100-D1111111"][1:], ["05:00:00", "22:01:00", 900, 1])
        self.assertEqual(freq["r101-D0000011"][1:], ["07:00:00", "20:01:00", 900, 1])
        self.assertNotIn("r104-D1111100-0700", freq)  # single departures are plain trips
        cal = {c[0]: c[1:8] for c in self.feed["calendar.txt"]}
        self.assertEqual(cal["D1111100"], [1, 1, 1, 1, 1, 0, 0])

    def test_stop_times_are_ordered_and_reference_stops(self):
        stop_ids = {s[0] for s in self.feed["stops.txt"]}
        self.assertEqual(stop_ids, {"n1", "n2", "n3"})
        by_trip = {}
        for row in self.feed["stop_times.txt"]:
            self.assertIn(row[3], stop_ids)
            by_trip.setdefault(row[0], []).append(row)
        r101 = by_trip["r101-D1111100"]
        self.assertEqual([r[3] for r in r101], ["n3", "n2", "n1"])
        self.assertEqual(hms_to_s(r101[-1][1]) - hms_to_s(r101[0][1]), 600)  # duration=00:10
        r104 = by_trip["r104-D1111100-1715"]
        self.assertEqual(r104[0][1], "17:15:00")
        for rows in by_trip.values():
            times = [hms_to_s(r[1]) for r in rows]
            self.assertEqual(times, sorted(set(times)))
        names = {s[0]: s[1] for s in self.feed["stops.txt"]}
        self.assertEqual(names["n1"], "Trạm A")
        self.assertEqual(names["n3"], "Trạm xe buýt")  # no name in OSM

    def test_shapes_and_labels(self):
        shape_ids = {s[0] for s in self.feed["shapes.txt"]}
        self.assertEqual(shape_ids, {"r100", "r101", "r104"})
        trips = {t[2]: t for t in self.feed["trips.txt"]}
        self.assertEqual(trips["r100-D1111111"][5], "r100")
        self.assertEqual(trips["r104-D1111100-0700"][5], "r104")
        self.assertIn(b.LABEL, self.feed["feed_info.txt"][0][0])
        agencies = {a[1] for a in self.feed["agency.txt"]}
        self.assertEqual(agencies, {"Công ty X", "Y"})


if __name__ == "__main__":
    unittest.main()
