package db.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.jsp.tagext.TryCatchFinally;

import org.apache.jasper.tagplugins.jstl.core.Catch;
import org.eclipse.jdt.internal.compiler.ast.TryStatement;

import db.DBConnection;
import entity.Item;
import entity.Item.ItemBuilder;
import external.ExternalAPI;
import external.ExternalAPIFactory;
import jdk.internal.org.objectweb.asm.commons.TryCatchBlockSorter;

public class MySQLConnection implements DBConnection {

	private Connection conn;

	public MySQLConnection() {
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conn = DriverManager.getConnection(MySQLDBUtil.URL);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void close() {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setFavoriteItems(String userId, List<String> itemIds) {
		if (conn == null) {
			System.err.println("DB connection failed");
			return;
		}
		String query = "INSERT INTO history (user_id, item_id) VALUES (?, ?)";
		try {
			PreparedStatement statement = conn.prepareStatement(query);
			statement.setString(1, userId);
			for (String itemId : itemIds) {
				statement.setString(2, itemId);
				statement.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void unsetFavoriteItems(String userId, List<String> itemIds) {
		if (conn == null) {
			System.err.println("DB connection failed");
			return;
		}
		String query = "DELETE FROM history WHERE user_id = ? and item_id = ?";
		try {
			PreparedStatement statement = conn.prepareStatement(query);
			statement.setString(1, userId);
			for (String itemId : itemIds) {
				statement.setString(2, itemId);
				// write to the database
				statement.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Set<String> getFavoriteItemIds(String userId) {
		if (conn == null) {
			System.err.println("DB connection failed");
			return new HashSet<>();
		}
		Set<String> favoriteItems = new HashSet<>();
		try {

			String sql = "SELECT item_id from history WHERE user_id = ? ";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			ResultSet rs = statement.executeQuery();

			while (rs.next()) {
				String itemId = rs.getString("item_id");
				favoriteItems.add(itemId);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return favoriteItems;
	}

	@Override
	public Set<Item> getFavoriteItems(String userId) {
		if (conn == null) {
			System.err.println("DB connection failed");
			return new HashSet<>();
		}
		Set<String> itemIds = getFavoriteItemIds(userId);
		Set<Item> favoriteItems = new HashSet<>();
		try {
			String sql = "SELECT * FROM items WHERE item_id = ?";
			PreparedStatement stmt = conn.prepareStatement(sql);
			for (String itemId : itemIds) {
				stmt.setString(1, itemId);
				
				ResultSet rs = stmt.executeQuery();
				
				ItemBuilder builder = new ItemBuilder();
				
				while (rs.next()) {
					builder.setItemId(rs.getString("item_id"));
					builder.setName(rs.getString("name"));
					builder.setAddress(rs.getString("address"));
					builder.setImageUrl(rs.getString("image_url"));
					builder.setUrl(rs.getString("url"));
					builder.setCategories(getCategories(itemId));
					builder.setDistance(rs.getDouble("distance"));
					builder.setRating(rs.getDouble("rating"));
					
					favoriteItems.add(builder.build());
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return favoriteItems;
	}

	@Override
	public Set<String> getCategories(String itemId) {
		if (conn == null) {
			System.err.println("DB connection failed");
			return new HashSet<>();
		}
		Set<String> categories = new HashSet<>();
		try {
			String sql = "SELECT category from categories WHERE item_id = ? ";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, itemId);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				categories.add(rs.getString("category"));
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return categories;
	}

	@Override
	public List<Item> searchItems(double lat, double lon, String term) {
		// Connect to external API
		ExternalAPI api = ExternalAPIFactory.getExternalAPI(); // moved here
		List<Item> items = api.search(lat, lon, term);
		for (Item item : items) {
			// Save the item into our own db.
			saveItem(item);
		}
		return items;
	}

	@Override
	public void saveItem(Item item) {
		if (conn == null) {
			System.err.println("DB connection failed");
			return;
		}
		try {
			// First, insert into items table
			// sql injection
			// select * from users where username = '' AND password = '';

			// username: fakeuser ' OR 1 = 1; DROP --
			// select * from users where username = 'fakeuser ' OR 1 = 1 --' AND password
			// ='';

			String sql = "INSERT IGNORE INTO items VALUES (?, ?, ?, ?, ?, ?, ?)";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, item.getItemId());
			ps.setString(2, item.getName());
			ps.setDouble(3, item.getRating());
			ps.setString(4, item.getAddress());
			ps.setString(5, item.getImageUrl());
			ps.setString(6, item.getUrl());
			ps.setDouble(7, item.getDistance());
			ps.execute();

			sql = "INSERT IGNORE INTO categories VALUES(?, ?)";
			ps = conn.prepareStatement(sql);
			ps.setString(1, item.getItemId());
			for (String category : item.getCategories()) {
				ps.setString(2, category);
				ps.execute();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getFullname(String userId) {
		if (conn == null) {
			return "";
		}
		String name = "";
		try {
			String sql = "SELECT first_name, last_name from users WHERE user_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				name += String.join(" ", rs.getString("first_name"), rs.getString("last_name"));
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return name;
	}

	@Override
	public boolean verifyLogin(String userId, String password) {
		if (conn == null) {
			return false;
		}
		try {
			String sql = "SELECT user_id FROM users WHERE user_id = ? and password = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			statement.setString(2, password);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				return true;
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	@Override
	public boolean register(String userId, String password, String firstName, String lastName) {
		if (conn == null) {
			return false;
		}
		String query = "INSERT INTO users (user_id, password, first_name, last_name) VALUES (?, ?, ?, ?)";
		try {
			// check if user_id has been registered
			String sql = "SELECT user_id from users WHERE user_id = ? ";
			PreparedStatement statement1 = conn.prepareStatement(sql);
			statement1.setString(1, userId);
			ResultSet rs = statement1.executeQuery();
			String username = null;
			while (rs.next()) {
				username = rs.getString("user_id");
				if (username.equals(userId)) {
					return false;
				}
			}
			// not registered
			PreparedStatement statement = conn.prepareStatement(query);
			statement.setString(1, userId);
			statement.setString(2, password);
			statement.setString(3, firstName);
			statement.setString(4, lastName);
			statement.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return true;
	}

}
