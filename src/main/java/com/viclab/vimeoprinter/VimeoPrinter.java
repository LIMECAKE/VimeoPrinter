package com.viclab.vimeoprinter;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VimeoPrinter {
	
	List<VimeoModel> vimeoList = new ArrayList<VimeoModel>();
	boolean testMode = false;

	public static void main(String[] args) {
		System.out.println("VIMEO PRINTER");

		VimeoPrinter vimeoPrinter = new VimeoPrinter();

		List<String> argsList = new ArrayList<String>();
		List<String> optsList = new ArrayList<String>();

		if (args == null || args.length == 0) {
			System.out.println("인자가 없습니다");
			return;
		}
		for (int i = 0; i < args.length; i++) {
			switch (args[i].charAt(0)) {
				case '-':
					if (args[i].length() < 2) {
						throw new IllegalArgumentException("Not a valid argument: " + args[i]);
					}
					if (args[i].charAt(1) == '-') {
						throw new IllegalArgumentException("Not a valid argument: " + args[i]);
					}
					optsList.add(args[i].substring(1));
					break;
				default:
					argsList.add(args[i]);
					break;
			}
		}

		if (argsList.size() == 0) {
			System.out.println("토큰값이 없습니다");
		}
		if (optsList.contains("test")) {
			vimeoPrinter.testMode = true;
			System.out.println("TEST MODE");
		}

		System.out.println("토큰 : " + argsList.get(0));
		try {
			vimeoPrinter.getVimeoVideoList(1, argsList.get(0));
		} catch (Exception e) {
			System.out.println("오류가 발생했습니다\n" + e.getMessage());
			e.printStackTrace();
		}


	}

	private void onVimeoVideoFinished() {
		if (vimeoList == null) {
			System.out.println("완료 : 비메오 영상이 없습니다");
			return;
		}
		System.out.println("파일을 출력중입니다");
		try {
			File file = new File("./output.txt");
			if (file.exists()) {
				file.delete();
			}
			if (!file.exists()) {
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file);
			BufferedWriter writer = new BufferedWriter(fw);
			for (VimeoModel vimeoModel : vimeoList) {
			//	System.out.println(vimeoModel.embCode);
				writer.write(vimeoModel.embCode + "|" + vimeoModel.name + "|" + vimeoModel.date + "|" + vimeoModel.complete + "|" + vimeoModel.playable + "|" + vimeoModel.duration + "\n");
			}
			writer.close();
		} catch (Exception e) {
			System.out.println("오류가 발생했습니다\n" + e.getMessage());
			e.printStackTrace();
			return;
		}
		System.out.println("완료되었습니다");
	}

	private void getVimeoVideoList(int page, String authToken) {
		apiVimeoVideoList(page, authToken, null, new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				if (e.getMessage().contains("Too many follow-up requests")) {
					System.out.println("오류가 발생했습니다\n" + "토큰값을 확인하세요");
					e.printStackTrace();
					return;
				}
				System.out.println("오류가 발생했습니다\n" + e.getMessage());
				e.printStackTrace();
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String responseBody = null;
				try {
					responseBody = response.body().string();
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (responseBody == null || responseBody.trim().length() == 0) {
					System.out.println("오류가 발생했습니다\n" + "response null");
					return;
				}
				String finalResponseBody = responseBody;
				if (response.code() == 200) {
					try {
						JSONObject responseObject = new JSONObject(finalResponseBody);
						int totalSize = responseObject.optInt("total", 0);
						int currentPage = responseObject.optInt("page", 0);
						JSONObject pagingObject = responseObject.optJSONObject("paging");
						int lastPage = 0;
						if (pagingObject != null) {
							String lastPageString = pagingObject.optString("last");
							if (lastPageString != null && lastPageString.trim().length() > 0 && lastPageString.contains("=")) {
								lastPage = Integer.parseInt(lastPageString.substring(lastPageString.lastIndexOf("page=") + 5));
							}
						}
						if (page == 1) System.out.println("total content : " + totalSize + ", page : " + lastPage);
						JSONArray dataArray = responseObject.optJSONArray("data");
						if (dataArray == null) {
							System.out.println("오류가 발생했습니다\n" + "data null");
							return;
						}
						for (int index = 0; index < dataArray.length(); index ++) {
							JSONObject vimeoObject = dataArray.getJSONObject(index);
							String uriString = vimeoObject.optString("uri", "");
							String embCode = "";
							if (uriString != null && uriString.trim().length() > 0 && uriString.contains("/")) embCode = uriString.substring(uriString.lastIndexOf("/") + 1);
							String nameString = vimeoObject.optString("name");
							int duration = vimeoObject.optInt("duration");
							String dateString = vimeoObject.optString("created_time");
							JSONObject picturesObject = vimeoObject.optJSONObject("pictures");
							String thumbnail = "";
							if (picturesObject != null) thumbnail = picturesObject.optString("base_link");
							JSONArray downloadArray = vimeoObject.optJSONArray("download");
							String downloadUrl = "";
							if (downloadArray != null && downloadArray.length() > 0) downloadUrl = downloadArray.getJSONObject(0).optString("link");
							JSONObject uploadObject = vimeoObject.optJSONObject("download");
							int completed = 0;
							if (uploadObject != null) completed = uploadObject.optString("status", "").equals("complete") ? 1 : 0;
							int playable = vimeoObject.optBoolean("is_playable") ? 1 : 0;
							VimeoModel vimeoModel = new VimeoModel(embCode, nameString, dateString, downloadUrl, thumbnail, playable, completed, duration);
							vimeoList.add(vimeoModel);
						}
						if (page >= lastPage) {
							onVimeoVideoFinished();
							return;
						}
						if (testMode && page >= 5) {
							onVimeoVideoFinished();
							return;
						}
						getVimeoVideoList(page + 1, authToken);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("오류가 발생했습니다\n" + "response code " + response.code());
					return;
				}
			}
		});
	}

	private void apiVimeoVideoList(int page, String authToken, HashMap<String, String> headerParameters, Callback okHttpCallback) {
		try {
			HttpUrl.Builder builder = new HttpUrl.Builder()
					.scheme("https")
					.host("api.vimeo.com")
					.addPathSegments("me/videos")
					.addQueryParameter("page", String.valueOf(page));
					//.addQueryParameter("per_page", String.valueOf(100));
			Request.Builder requestBuilder = new Request.Builder();
			HttpUrl httpUrl;
			httpUrl = builder.build();
			requestBuilder = requestBuilder.url(httpUrl).get();

			if (headerParameters == null) headerParameters = new HashMap<>();
			//headerParameters.put("page", String.valueOf(page));
			//headerParameters.put("per_page", String.valueOf(100));
			for (Map.Entry<String, String> entry : headerParameters.entrySet()) {
				requestBuilder = requestBuilder.addHeader(entry.getKey(), entry.getValue());
			}

			Request request = requestBuilder.build();
			OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder();
			OkHttpClient okHttpClient = okHttpBuilder
					.connectTimeout(600, TimeUnit.SECONDS)
					.writeTimeout(600, TimeUnit.SECONDS)
					.readTimeout(600, TimeUnit.SECONDS)
					.authenticator(getAuthenticator(authToken)).build();
			okHttpClient.newCall(request).enqueue(okHttpCallback);
			System.out.println("reading page " + page);

			return;
		} catch(Exception e) {
			e.printStackTrace();
		}
		return;
	}

	private static Authenticator getAuthenticator(final String authToken) {
		return (route, response) -> response.request().newBuilder().header("Authorization", "bearer " + authToken).build();
	}

	public class VimeoModel {

		public String embCode = "";
		public String name = "";
		public String date = "";
		public String download = "";
		public String thumbnail = "";
		public int playable = 0;
		public int complete = 0;
		public int duration = 0;

		public VimeoModel(String embCode, String name, String date, String download, String thumbnail, int playable, int complete, int duration) {
			this.embCode = embCode;
			this.name = name;
			this.date = date;
			this.download = download;
			this.thumbnail = thumbnail;
			this.playable = playable;
			this.complete = complete;
			this.duration = duration;
		}

	}

}
