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

import java.util.ArrayList;

import com.appnexus.opensdk.R;
import com.appnexus.opensdk.utils.Clog;
import com.appnexus.opensdk.utils.Settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;

/**
 * The parent class of InterstitialAdView and BannerAdView. This can not be
 * instantiated.
 * 
 * @author jacob
 * 
 */
public abstract class AdView extends FrameLayout {

	protected AdFetcher mAdFetcher;
	private String placementID;
	protected boolean opensNativeBrowser = false;
	protected int measuredWidth;
	protected int measuredHeight;
	private boolean measured = false;
	protected int width = -1;
	protected int height = -1;
	protected boolean shouldServePSAs = true;
	private boolean mraid_expand = false;
	protected AdListener adListener;
	private BrowserStyle browserStyle;

	/** Begin Construction **/

	@SuppressWarnings("javadoc")
	public AdView(Context context) {
		super(context, null);
		setup(context, null);
	}

	public AdView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setup(context, attrs);

	}

	public AdView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setup(context, attrs);
	}

	protected void setup(Context context, AttributeSet attrs) {
		// Store self.context in the settings for errors
		Clog.error_context = this.getContext();

		Clog.d(Clog.publicFunctionsLogTag, Clog.getString(R.string.new_adview));

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (prefs.getBoolean("opensdk_first_launch", true)) {
			// This is the first launch, store a value to remember
			Clog.v(Clog.baseLogTag,
					Clog.getString(R.string.first_opensdk_launch));
			Settings.getSettings().first_launch = true;
			prefs.edit().putBoolean("opensdk_first_launch", false).commit();
		} else {
			// Found the stored value, this is NOT the first launch
			Clog.v(Clog.baseLogTag,
					Clog.getString(R.string.not_first_opensdk_launch));
			Settings.getSettings().first_launch = false;
		}

		// Store the UA in the settings
		Settings.getSettings().ua = new WebView(context).getSettings()
				.getUserAgentString();
		Clog.v(Clog.baseLogTag,
				Clog.getString(R.string.ua, Settings.getSettings().ua));

		// Store the AppID in the settings
		Settings.getSettings().app_id = context.getApplicationContext()
				.getPackageName();
		Clog.v(Clog.baseLogTag,
				Clog.getString(R.string.appid, Settings.getSettings().app_id));

		Clog.v(Clog.baseLogTag, Clog.getString(R.string.making_adman));
		// Make an AdFetcher - Continue the creation pass
		mAdFetcher = new AdFetcher(this);
		// Load user variables only if attrs isn't null
		if (attrs != null)
			loadVariablesFromXML(context, attrs);

		// We don't start the ad requesting here, since the view hasn't been
		// sized yet.
	}

	@Override
	public void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (mraid_expand) {
			mraid_expand = false;
			return;
		}
		if (!measured || changed) {
			// Convert to dips
			float density = getContext().getResources().getDisplayMetrics().density;
			measuredWidth = (int) ((right - left) / density + 0.5f);
			measuredHeight = (int) ((bottom - top) / density + 0.5f);
			if ((measuredHeight < height || measuredWidth < width) && measuredHeight>0 && measuredWidth > 0) {
				Clog.e(Clog.baseLogTag, Clog.getString(R.string.adsize_too_big,
						measuredWidth, measuredHeight, width, height));
				// Hide the space, since no ad will be loaded due to error
				hide();
				// Stop any request in progress
				if (mAdFetcher != null)
					mAdFetcher.stop();
				// Returning here allows the SDK to re-request when the layout
				// next changes, and maybe the error will be amended.
				return;
			}

			// Hide the adview
			if (!measured){
				hide();
				onFirstLayout();
			}

			measured = true;
			

		}
	}

	// If single-use mode, we must manually start the fetcher
	protected void onFirstLayout() {
		// If an MRAID ad is expanded here, don't fetch on resume.
		if (isMRAIDExpanded())
			return;
		mAdFetcher.start();
	}

	protected boolean isMRAIDExpanded() {
		if (this.getChildCount() > 0
				&& this.getChildAt(0) instanceof MRAIDWebView
				&& ((MRAIDWebView) getChildAt(0)).getImplementation().expanded) {
			return true;
		}
		return false;
	}

	/**
	 * Loads a new ad, if the ad space is visible.
	 */
	public void loadAd() {
		if (this.getWindowVisibility() == VISIBLE && mAdFetcher != null) {
			// Reload Ad Fetcher to get new ad at user's request
			mAdFetcher.stop();
			mAdFetcher.clearDurations();
			mAdFetcher.start();
		}
	}

	/**
	 * Loads a new ad, if the ad space is visible, and sets the placement id
	 * attribute of the AdView to the supplied parameter.
	 * 
	 * @param placementID
	 *            The new placement id to use.
	 */
	public void loadAd(String placementID) {
		this.setPlacementID(placementID);
		loadAd();
	}

	/**
	 * Loads a new ad, if the ad space is visible, and sets the placement id, ad
	 * width, and ad height attribute of the AdView to the supplied parameters.
	 * 
	 * @param placementID
	 *            The new placement id to use.
	 * @param width
	 *            The new width to use.
	 * @param height
	 *            The new height to use.
	 */
	public void loadAd(String placementID, int width, int height) {
		this.setAdHeight(height);
		this.setAdWidth(width);
		this.setPlacementID(placementID);
		loadAd();
	}

	public void loadHtml(String content, int width, int height) {
		this.mAdFetcher.stop();

		AdWebView awv = new AdWebView(this);
		awv.loadData(content, "text/html", "UTF-8");
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width,
				height);
		awv.setLayoutParams(lp);
		this.display(awv);
	}

	protected abstract void loadVariablesFromXML(Context context,
			AttributeSet attrs);

	/*
	 * End Construction
	 */

	protected void display(Displayable d) {
		if (d == null) {
			if (this.adListener != null)
				adListener.onAdRequestFailed(this);
			return;
		}
		if (d.failed()) {
			if (this.adListener != null)
				adListener.onAdRequestFailed(this);
			return; // The displayable has failed to be parsed or turned into a
					// View.
		}
		this.removeAllViews();
		this.addView(d.getView());
		if (this.adListener != null)
			adListener.onAdLoaded(this);
		unhide();
	}

	protected void unhide() {
		if (getVisibility() != VISIBLE) {
			setVisibility(VISIBLE);
		}
	}

	protected void hide() {
		if (getVisibility() != GONE)
			setVisibility(GONE);
	}

	/**
	 * 
	 * @return The current placement id.
	 */
	public String getPlacementID() {
		Clog.d(Clog.publicFunctionsLogTag,
				Clog.getString(R.string.get_placement_id, placementID));
		return placementID;
	}

	/**
	 * Sets the placement id of the AdView.
	 * 
	 * @param placementID
	 *            The placement id to use
	 */
	public void setPlacementID(String placementID) {
		Clog.d(Clog.publicFunctionsLogTag,
				Clog.getString(R.string.set_placement_id, placementID));
		this.placementID = placementID;
	}

	@Override
	protected void finalize() {
		try {
			super.finalize();
		} catch (Throwable e) {
		}
		// Just in case, kill the adfetcher's service
		if (mAdFetcher != null)
			mAdFetcher.stop();
	}

	/**
	 * Sets the height of the ad to request.
	 * 
	 * @param h
	 *            The height, in pixels, to use.
	 */
	public void setAdHeight(int h) {
		Clog.d(Clog.baseLogTag, Clog.getString(R.string.set_height, h));
		height = h;
	}

	/**
	 * Sets the width of the ad to request.
	 * 
	 * @param w
	 *            The width, in pixels, to use.
	 */
	public void setAdWidth(int w) {
		Clog.d(Clog.baseLogTag, Clog.getString(R.string.set_width, w));
		width = w;
	}

	/**
	 * @return The height of the ad to be requested.
	 */
	public int getAdHeight() {
		Clog.d(Clog.baseLogTag, Clog.getString(R.string.get_height, height));
		return height;
	}

	/**
	 * 
	 * @return The width of the ad to be requested.
	 */
	public int getAdWidth() {
		Clog.d(Clog.baseLogTag, Clog.getString(R.string.get_width, width));
		return width;
	}

	protected int getContainerWidth() {
		return measuredWidth;
	}

	protected int getContainerHeight() {
		return measuredHeight;
	}

	// Used only by MRAID
	private ImageButton close;

	protected void expand(int w, int h, boolean custom_close,
			final MRAIDImplementation caller) {
		// Only expand w and h if they are >0, otherwise they are match_parent
		// or something
		mraid_expand = true;
		if (getLayoutParams() != null) {
			if (getLayoutParams().width > 0)
				getLayoutParams().width = w;
			if (getLayoutParams().height > 0)
				getLayoutParams().height = h;
		}
		if (!custom_close && close == null) {
			// Add a stock close button to the top right corner
			close = new ImageButton(this.getContext());
			close.setImageDrawable(getResources().getDrawable(
					android.R.drawable.ic_menu_close_clear_cancel));
			FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.WRAP_CONTENT,
					FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.RIGHT
							| Gravity.TOP);
			blp.rightMargin = (this.getMeasuredWidth() - this.getChildAt(0)
					.getMeasuredWidth()) / 2;
			close.setLayoutParams(blp);
			close.setBackgroundColor(Color.TRANSPARENT);
			close.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					caller.close();

				}
			});
			this.addView(close);
		} else if (custom_close && close != null) {
			close.setVisibility(GONE);
		} else if (!custom_close && close != null) {
			this.removeView(close);
			close.setVisibility(VISIBLE);
			this.addView(close);// Re-add to send to top
		}
	}

	abstract String getMRAIDAdType();

	/**
	 * Sets the listener that the InterstitialAdView will call events in.
	 * 
	 * @param listener
	 *            The {@link AdListener} object to use.
	 */
	public void setAdListener(AdListener listener) {
		Clog.d(Clog.publicFunctionsLogTag,
				Clog.getString(R.string.set_ad_listener));
		adListener = listener;
	}

	/**
	 * Gets the listener that the InterstitialAdView will call events in.
	 * 
	 * @return The {@link AdListener} object in use.
	 */
	public AdListener getAdListener() {
		Clog.d(Clog.publicFunctionsLogTag,
				Clog.getString(R.string.get_ad_listener));
		return adListener;
	}

	protected void fail() {
		if (adListener != null)
			this.post(new Runnable() {

				@Override
				public void run() {
					AdView.this.adListener.onAdRequestFailed(AdView.this);
				}

			});
	}

	/**
	 * @return whether or not the native browser is used instead of the in-app
	 *         browser.
	 */
	public boolean getOpensNativeBrowser() {
		Clog.d(Clog.publicFunctionsLogTag, Clog.getString(
				R.string.get_opens_native_browser, opensNativeBrowser));
		return opensNativeBrowser;
	}

	/**
	 * Set this to true to disable the in-app browser.
	 * 
	 * @param opensNativeBrowser
	 */
	public void setOpensNativeBrowser(boolean opensNativeBrowser) {
		Clog.d(Clog.publicFunctionsLogTag, Clog.getString(
				R.string.set_opens_native_browser, opensNativeBrowser));
		this.opensNativeBrowser = opensNativeBrowser;
	}

	protected BrowserStyle getBrowserStyle() {
		return browserStyle;
	}

	protected void setBrowserStyle(BrowserStyle browserStyle) {
		this.browserStyle = browserStyle;
	}

	/**
	 * @return Whether this placement accepts PSAs if no ad is served.
	 */
	public boolean getShouldServePSAs() {
		return shouldServePSAs;
	}

	/**
	 * @param shouldServePSAs
	 *            Whether this placement accepts PSAs if no ad is served.
	 */
	public void setShouldServePSAs(boolean shouldServePSAs) {
		this.shouldServePSAs = shouldServePSAs;
	}

    static class BrowserStyle {

		public BrowserStyle(Drawable forwardButton, Drawable backButton,
				Drawable refreshButton) {
			this.forwardButton = forwardButton;
			this.backButton = backButton;
			this.refreshButton = refreshButton;
		}

		Drawable forwardButton;
		Drawable backButton;
		Drawable refreshButton;

		static final ArrayList<Pair<String, BrowserStyle>> bridge = new ArrayList<Pair<String, BrowserStyle>>();
	}
}
