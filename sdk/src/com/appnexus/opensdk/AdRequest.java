/*
 *    Copyright 2013 APPNEXUS INC
 *    
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *    
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.appnexus.opensdk;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import com.appnexus.opensdk.InterstitialAdView.Size;
import com.appnexus.opensdk.utils.Clog;
import com.appnexus.opensdk.utils.HashingFunctions;
import com.appnexus.opensdk.utils.Settings;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;

/**
 * @author jacob
 * 
 */
public class AdRequest extends AsyncTask<Void, Integer, AdResponse> {

	AdView owner;
	AdRequester requester;
	AdListener adListener;
	Context context;
	String hidmd5;
	String hidsha1;
	String devMake;
	String devModel;
	String carrier;
	String firstlaunch;
	String lat;
	String lon;
	String locDataAge;
	String locDataPrecision;
	String ua;
	String orientation;
	String allowedSizes;
	String mcc;
	String mnc;
	String connection_type;
	String dev_time; // Set at the time of the request
	String dev_timezone;
	String os;
	String language;
	String placementId;
	String nativeBrowser;
	int width = -1;
	int height = -1;
	int maxWidth = -1;
	int maxHeight = -1;

	/**
	 * Creates a new AdRequest with the given parameters
	 * 
	 * @param requester
	 * @param aid
	 * @param lat
	 * @param lon
	 * @param placementId
	 * @param orientation
	 * @param carrier
	 * @param width
	 * @param height
	 * @param maxWidth
	 * @param maxHeight
	 * @param mcc
	 * @param mnc
	 * @param connectionType
	 * @param isNativeBrowser
	 * @param adListener
	 */
	public AdRequest(AdRequester requester, String aid, String lat, String lon,
			String placementId, String orientation, String carrier, int width,
			int height, int maxWidth, int maxHeight, String mcc, String mnc,
			String connectionType, boolean isNativeBrowser,
			AdListener adListener) {
		this.adListener = adListener;
		this.requester = requester;
		if (aid != null) {
			hidmd5 = HashingFunctions.md5(aid);
			hidsha1 = HashingFunctions.sha1(aid);
		}
		devMake = Settings.getSettings().deviceMake;
		devModel = Settings.getSettings().deviceModel;

		// Get firstlaunch and convert it to a string
		firstlaunch = "" + Settings.getSettings().first_launch;
		// Get ua, the user agent...
		ua = Settings.getSettings().ua;

		this.lat = lat;
		this.lon = lon;

		this.carrier = carrier;

		this.mnc = mnc;
		this.mcc = mcc;

		this.width = width;
		this.height = height;
		this.maxWidth = maxWidth;
		this.maxHeight = maxHeight;

		this.connection_type = connectionType;
		this.dev_time = "" + System.currentTimeMillis();

		this.dev_timezone = Settings.getSettings().dev_timezone;
		this.os = Settings.getSettings().os;
		this.language = Settings.getSettings().language;

		this.placementId = placementId;

		this.nativeBrowser = isNativeBrowser ? "1" : "0";
	}

	public AdRequest(AdFetcher fetcher) {
		this.owner = fetcher.owner;
		this.requester = fetcher;
		this.placementId = owner.getPlacementID();
		context = owner.getContext();
		String aid = android.provider.Settings.Secure.getString(
				context.getContentResolver(), Secure.ANDROID_ID);

		// Do we have access to location?
		if (context
				.checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED
				|| context
						.checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED) {
			// Get lat, long from any GPS information that might be currently
			// available
			LocationManager lm = (LocationManager) context
					.getSystemService(Context.LOCATION_SERVICE);
			Location lastLocation = lm.getLastKnownLocation(lm.getBestProvider(
					new Criteria(), false));
			if (lastLocation != null) {
				lat = "" + lastLocation.getLatitude();
				lon = "" + lastLocation.getLongitude();
				locDataAge = ""
						+ (System.currentTimeMillis() - lastLocation.getTime());
				locDataPrecision = "" + lastLocation.getAccuracy();
			}
		} else {
			Clog.w(Clog.baseLogTag,
					Clog.getString(R.string.permissions_missing_location));
		}

		// Do we have permission ACCESS_NETWORK_STATE?
		if (context
				.checkCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE") != PackageManager.PERMISSION_GRANTED) {
			Clog.e(Clog.baseLogTag,
					Clog.getString(R.string.permissions_missing_network_state));
			fail();
			this.cancel(true);
			return;
		}

		// Get orientation, the current rotation of the device
		orientation = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape"
				: "portrait";
		// Get hidmd5, hidsha1, the device ID hashed
		if (Settings.getSettings().hidmd5 == null) {
			Settings.getSettings().hidmd5 = HashingFunctions.md5(aid);
		}
		hidmd5 = Settings.getSettings().hidmd5;
		if (Settings.getSettings().hidsha1 == null) {
			Settings.getSettings().hidsha1 = HashingFunctions.sha1(aid);
		}
		hidsha1 = Settings.getSettings().hidsha1;
		// Get devMake, devModel, the Make and Model of the current device
		devMake = Settings.getSettings().deviceMake;
		devModel = Settings.getSettings().deviceModel;
		// Get carrier
		if (Settings.getSettings().carrierName == null) {
			Settings.getSettings().carrierName = ((TelephonyManager) context
					.getSystemService(Context.TELEPHONY_SERVICE))
					.getNetworkOperatorName();
		}
		carrier = Settings.getSettings().carrierName;
		// Get firstlaunch and convert it to a string
		firstlaunch = "" + Settings.getSettings().first_launch;
		// Get ua, the user agent...
		ua = Settings.getSettings().ua;
		// Get wxh
		this.width = owner.getAdWidth();
		this.height = owner.getAdHeight();

		maxHeight = owner.getContainerHeight();
		maxWidth = owner.getContainerWidth();

		if (Settings.getSettings().mcc == null
				|| Settings.getSettings().mnc == null) {
			TelephonyManager tm = (TelephonyManager) context
					.getSystemService(Context.TELEPHONY_SERVICE);
			String networkOperator = tm.getNetworkOperator();
			if (networkOperator != null && networkOperator.length() >= 6) {
				Settings.getSettings().mcc = networkOperator.substring(0, 3);
				Settings.getSettings().mnc = networkOperator.substring(3);
			}
		}
		mcc = Settings.getSettings().mcc;
		mnc = Settings.getSettings().mnc;

		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		connection_type = wifi.isConnected() ? "wifi" : "wan";
		dev_time = "" + System.currentTimeMillis();

		if (owner instanceof InterstitialAdView) {
			// Make string for allowed_sizes
			allowedSizes = "";
			for (Size s : ((InterstitialAdView) owner).getAllowedSizes()) {
				allowedSizes += "" + s.width() + "x" + s.height();
				// If not last size, add a comma
				if (((InterstitialAdView) owner).getAllowedSizes().indexOf(s) != ((InterstitialAdView) owner)
						.getAllowedSizes().size() - 1)
					allowedSizes += ",";
			}
		}

		nativeBrowser = owner.getOpensNativeBrowser() ? "1" : "0";

		mcc = Settings.getSettings().mcc;
		mnc = Settings.getSettings().mnc;
		os = Settings.getSettings().os;
		language = Settings.getSettings().language;
	}

	private void fail() {
		if (requester != null)
			requester.failed(this);
		if (adListener != null)
			adListener.onAdRequestFailed(null);
		Clog.clearLastResponse();
	}

	public String getRequestUrl() {
		StringBuilder sb = new StringBuilder(Settings.getSettings().BASE_URL);
		sb.append((placementId != null ? "id=" + Uri.encode(placementId)
				: "id=NO-PLACEMENT-ID"));
		sb.append((!isEmpty(hidmd5) ? "&md5udid=" + Uri.encode(hidmd5) : ""));
		sb.append((!isEmpty(hidsha1) ? "&sha1udid=" + Uri.encode(hidsha1) : ""));
		sb.append((!isEmpty(devMake) ? "&devmake=" + Uri.encode(devMake) : ""));
		sb.append((!isEmpty(devModel) ? "&devmodel=" + Uri.encode(devModel)
				: ""));
		sb.append((!isEmpty(carrier) ? "&carrier=" + Uri.encode(carrier) : ""));
		sb.append((!isEmpty(Settings.getSettings().app_id) ? "&appid="
				+ Uri.encode(Settings.getSettings().app_id)
				: "&appid=NO-APP-ID"));
		sb.append((!isEmpty(firstlaunch) ? "&firstlaunch=" + firstlaunch : ""));
		sb.append(!isEmpty(lat) && !isEmpty(lon) ? "&loc=" + lat + "," + lon
				: "");
		sb.append((!isEmpty(locDataAge) ? "&" + "loc_age=" + locDataAge : ""));
		sb.append((!isEmpty(locDataPrecision) ? "&loc_prec=" + locDataPrecision
				: ""));
		sb.append((Settings.getSettings().test_mode ? "&istest=true" : ""));
		sb.append((!isEmpty(ua) ? "&ua=" + Uri.encode(ua) : ""));
		sb.append((!isEmpty(orientation) ? "&orientation=" + orientation : ""));
		sb.append(((width > 0 && height > 0) ? "&size=" + width + "x" + height
				: ""));
		if (owner != null) {
			if(maxHeight>0 && maxWidth >0){
				if(!(owner instanceof InterstitialAdView) && (width<0 || height<0)){
					sb.append("&max_size="
							+ maxWidth + "x" + maxHeight);
				}else{
					sb.append("&size="
							+ maxWidth + "x" + maxHeight);
				}
			}
		}
		sb.append((!isEmpty(allowedSizes) ? "&promo_sizes=" + allowedSizes : ""));
		sb.append((!isEmpty(mcc) ? "&mcc=" + Uri.encode(mcc) : ""));
		sb.append((!isEmpty(mnc) ? "&mnc=" + Uri.encode(mnc) : ""));
		sb.append((!isEmpty(os) ? "&os=" + Uri.encode(os) : ""));
		sb.append((!isEmpty(language) ? "&language=" + Uri.encode(language)
				: ""));
		sb.append((!isEmpty(dev_timezone) ? "&devtz="
				+ Uri.encode(dev_timezone) : ""));
		sb.append((!isEmpty(dev_time) ? "&devtime=" + Uri.encode(dev_time) : ""));
		sb.append((!isEmpty(connection_type) ? "&connection_type="
				+ Uri.encode(connection_type) : ""));
		sb.append((!isEmpty(nativeBrowser) ? "&native_browser=" + nativeBrowser
				: ""));
		sb.append("&format=json");
		sb.append("&sdkver=" + Uri.encode(Settings.getSettings().sdkVersion));

		return sb.toString();
	}

	@Override
	protected AdResponse doInBackground(Void... params) {
		if (context != null) {
			NetworkInfo ninfo = ((ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE))
					.getActiveNetworkInfo();
			if (ninfo == null || !ninfo.isConnectedOrConnecting()) {
				Clog.d(Clog.httpReqLogTag,
						Clog.getString(R.string.no_connectivity));
				fail();
				return null;
			}
		}
		String query_string = getRequestUrl();

		Clog.setLastRequest(query_string);

		Clog.d(Clog.httpReqLogTag,
				Clog.getString(R.string.fetch_url, query_string));

		HttpResponse r = null;
		String out = null;
		try {
			HttpParams p = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(p,
					Settings.getSettings().HTTP_CONNECTION_TIMEOUT);
			HttpConnectionParams.setSoTimeout(p,
					Settings.getSettings().HTTP_SOCKET_TIMEOUT);
			HttpConnectionParams.setSocketBufferSize(p, 8192);
			DefaultHttpClient h = new DefaultHttpClient(p);
			r = h.execute(new HttpGet(query_string));
			if (!httpShouldContinue(r.getStatusLine())) {
				fail();
				return new AdResponse(null, AdResponse.http_error, null);
			}
			out = EntityUtils.toString(r.getEntity());
		} catch (ClientProtocolException e) {
			Clog.e(Clog.httpReqLogTag, Clog.getString(R.string.http_unknown));
			fail();
			return null;
		} catch (ConnectTimeoutException e) {
			Clog.e(Clog.httpReqLogTag, Clog.getString(R.string.http_timeout));
			fail();
			return null;
		} catch (HttpHostConnectException e) {
			HttpHostConnectException he = (HttpHostConnectException) e;
			Clog.e(Clog.httpReqLogTag, Clog.getString(
					R.string.http_unreachable, he.getHost().getHostName(), he
							.getHost().getPort()));
			fail();
			return null;
		} catch (IOException e) {
			Clog.e(Clog.httpReqLogTag, Clog.getString(R.string.http_io));
			fail();
			return null;
		} catch (SecurityException se) {
			fail();
			Clog.e(Clog.baseLogTag,
					Clog.getString(R.string.permissions_internet));
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			Clog.e(Clog.baseLogTag, Clog.getString(R.string.unknown_exception));
			fail();
			return null;
		}
		if (out.equals("")) {
			fail();
			return null;
		}
		return new AdResponse(requester, out, r.getAllHeaders());
	}

	private boolean httpShouldContinue(StatusLine statusLine) {
		int http_error_code = statusLine.getStatusCode();
		switch (http_error_code) {
		default:
			Clog.d(Clog.httpRespLogTag,
					Clog.getString(R.string.http_bad_status, http_error_code));
			return false;
		case 200:
			return true;
		}

	}

	@Override
	protected void onPostExecute(AdResponse result) {
		if (result == null) {
			Clog.v(Clog.httpRespLogTag, Clog.getString(R.string.no_response));
			// Don't call fail again!
			return; // http request failed
		}
		if (requester != null)
			requester.onReceiveResponse(result);
		if (adListener != null)
			adListener.onAdLoaded(null);
	}

	private boolean isEmpty(String str) {
		if (str == null)
			return true;
		if (str.equals(""))
			return true;
		return false;
	}

}