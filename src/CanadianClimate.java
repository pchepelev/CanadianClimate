import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

public class CanadianClimate {

	private static final String PREFIX = "google.visualization.Query.setResponse(";
	private static final String SUFFIX = ");";
	static JsonFactory mFactory = new JsonFactory();

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws IOException, InterruptedException {
		BufferedReader br = new BufferedReader(new FileReader("categories.csv"));
		String line = null;
		List<Map<String, Object>> lehLizt = new ArrayList<Map<String, Object>>();
		while ((line = br.readLine()) != null) {
			Map<String, Object> catMap = new HashMap<String, Object>();
			Map<String, String> cityMap = new HashMap<String, String>();
			String[] splitLine = line.split(",");
			String category = splitLine[0];
			String name = splitLine[1];
			String desc = splitLine[2];
			catMap.put("name", name);
			catMap.put("desc", desc);

			Map<String, String> categoryMap = requestCategoryList(category);
			Iterator<Entry<String, String>> iterator = categoryMap.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<String, String> entry = iterator.next();
				cityMap.put(entry.getKey(), entry.getValue());
			}
			catMap.put("chart", cityMap);
			lehLizt.add(catMap);
		}
		br.close();

		FileWriter fw = new FileWriter("climate.csv");
		fw.write("name,description");
		Map<String, Integer> city2Index = new HashMap<String, Integer>();
		Map<String, Object> firstMap = lehLizt.get(0);
		Map<String, String> firstChart = (Map<String, String>) firstMap.get("chart");
		Iterator<Entry<String, String>> firstChartIterator = firstChart.entrySet().iterator();
		int i = 0;
		while (firstChartIterator.hasNext()) {
			Entry<String, String> entry = firstChartIterator.next();
			String cityName = entry.getKey();
			city2Index.put(cityName, i++);
			fw.write("," + cityName);
		}
		fw.write("\n");

		for (Map<String, Object> cat : lehLizt) {
			String categoryName = (String) cat.get("name");
			fw.write(categoryName + ",");

			String categoryDescription = (String) cat.get("desc");
			fw.write(categoryDescription);

			System.out.println("Writing category " + categoryName);
			Map<String, Object> categoryChart = (Map<String, Object>) cat.get("chart");
			String[] valueArray = new String[city2Index.size()];
			Iterator<Entry<String, Object>> catChartIterator = categoryChart.entrySet().iterator();
			System.out.print("City: ");
			while (catChartIterator.hasNext()) {
				Entry<String, Object> cityValuePair = catChartIterator.next();
				String city = cityValuePair.getKey();
				String value = (String) cityValuePair.getValue();
				Integer colIdx = city2Index.get(city);
				valueArray[colIdx] = value;
				System.out.print(city + ",");
			}
			System.out.println();

			for (int j = 0; j < valueArray.length; j++) {
				fw.write("," + valueArray[j]);
			}
			fw.write("\n");
		}
		fw.close();
	}

	private static Map<String, String> requestCategoryList(String pCategory) throws IOException, InterruptedException {
		System.out.println("Requesting category " + pCategory);
		HttpRequest request = HttpRequest
				.newBuilder(URI.create("https://www.weatherstats.ca/winners-chart.js?category=" + pCategory)).build();
		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		String body = response.body();

		Map<String, String> retMap = new HashMap<String, String>();
		int ioSuffix = body.indexOf(SUFFIX);
		body = body.substring(PREFIX.length(), ioSuffix);
		body = body.replaceAll("'([a-zA-Z0-9]+)'", "\"$1\"");
		body = body.replaceAll("([{,])\\s*([a-zA-Z0-9]+)\\s*:", "$1\"$2\":");

		JsonParser parser = mFactory.createParser(body);
		while (!parser.isClosed()) {
			parser.nextToken();
			String tokenValue = parser.getValueAsString();
			if (tokenValue != null && tokenValue.contains("Value:")) {
				String cityName = tokenValue.substring("<B>".length(), tokenValue.indexOf("</B>"));
				String value = tokenValue.substring(tokenValue.lastIndexOf("Value:") + "Value:".length(),
						tokenValue.length());
				Pattern pattern = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");
				Matcher matcher = pattern.matcher(value);
				matcher.find();
				int startIndex = matcher.start();
				int endIndex = matcher.end();
				String parsedValue = value.substring(startIndex, endIndex);
				retMap.put(cityName, parsedValue);
			}
		}
		Thread.sleep(1000);
		return retMap;
	}

}
