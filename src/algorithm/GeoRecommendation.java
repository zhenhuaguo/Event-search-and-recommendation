package algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import db.DBConnection;
import db.DBConnectionFactory;
import entity.Item;

public class GeoRecommendation {
	public List<Item> recommendItems(String userId, double lat, double lon) {
		List<Item> recommendedItems = new ArrayList<>();

		DBConnection conn = DBConnectionFactory.getDBConnection();

		try {
			// step 1 Get all favorite items
			Set<String> favoriteItems = conn.getFavoriteItemIds(userId);

			// step 2 Get all categories of favorite items, sort by count
			Map<String, Integer> allCategories = new HashMap<>();
			for (String item : favoriteItems) {
				Set<String> categories = conn.getCategories(item);

				for (String category : categories) {
					allCategories.put(category, allCategories.getOrDefault(category, 0) + 1);
				}
			}

			List<Entry<String, Integer>> categoryList = new ArrayList<>(allCategories.entrySet());
			Collections.sort(categoryList, (Entry<String, Integer> o1, Entry<String, Integer> o2) -> {
				return Integer.compare(o2.getValue(), o1.getValue());
			});

			// step 3 do search based on category, filter out favorite events, sort by
			// distance
			Set<Item> visitedItems = new HashSet<>();
			for (Entry<String, Integer> category : categoryList) {
				List<Item> items = conn.searchItems(lat, lon, category.getKey());
				List<Item> filteredItems = new ArrayList<>();

				for (Item item : items) {
					if (!favoriteItems.contains(item.getItemId()) && !visitedItems.contains(item)) {
						filteredItems.add(item);
					}
				}

				Collections.sort(filteredItems, (Item item1, Item item2) -> {
					return Double.compare(item1.getDistance(), item2.getDistance());
				});

				visitedItems.addAll(items);
				recommendedItems.addAll(filteredItems);
			}

		} finally {
			conn.close();
		}

		return recommendedItems;
	}

	// Calculate the distances between two geolocations.
	// Source : http://andrew.hedges.name/experiments/haversine/
	private static double getDistance(double lat1, double lon1, double lat2, double lon2) {
		double dlon = lon2 - lon1;
		double dlat = lat2 - lat1;
		double a = Math.sin(dlat / 2 / 180 * Math.PI) * Math.sin(dlat / 2 / 180 * Math.PI)
				+ Math.cos(lat1 / 180 * Math.PI) * Math.cos(lat2 / 180 * Math.PI) * Math.sin(dlon / 2 / 180 * Math.PI)
						* Math.sin(dlon / 2 / 180 * Math.PI);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		// Radius of earth in miles.
		double R = 3961;
		return R * c;
	}

}
