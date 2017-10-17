/*
 * Copyright 2017 FUJITSU CLOUD TECHNOLOGIES LIMITED All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nifty.cloud.mb.core;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * NCMBRichPush provide dialog for rich push notification
 */
public class NCMBRichPush extends Dialog {

    private static final FrameLayout.LayoutParams FILL = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    private static final String GOOGLE_DOCS_BASE_VIEWER_URL = "http://docs.google.com/gview?embedded=true&url=";
    private static final String BTN_NEXT_TXT = "Next";
    private static final String BTN_PREVIOUS = "Previous";
    private LinearLayout webBackView;
    private FrameLayout richPushHandlerContainer;
    private ImageView closeImage;
    private ImageView mImageView;
    private String requestUrl;
    private ProgressDialog progressDialog;
    /**
     * {@link android.widget.Button} to move to the previous page.
     */
    private Button mButtonPrevious;

    /**
     * {@link android.widget.Button} to move to the next page.
     */
    private Button mButtonNext;
    /**
     * File descriptor of the PDF.
     */
    private ParcelFileDescriptor mFileDescriptor;

    /**
     * {@link android.graphics.pdf.PdfRenderer} to render the PDF.
     */
    private PdfRenderer mPdfRenderer;
    /**
     * Page that is currently shown on the screen.
     */
    private PdfRenderer.Page mCurrentPage;

    public NCMBRichPush(Context context, String requestUrl) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.requestUrl = requestUrl;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.progressDialog = new ProgressDialog(getContext());
        this.progressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.progressDialog.setMessage("Loading...");

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.richPushHandlerContainer = new FrameLayout(getContext());

        createCloseImage();

        setUpWebView();

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        this.richPushHandlerContainer.addView(this.closeImage, layoutParams);

        layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        mButtonPrevious = new Button(getContext());
        mButtonPrevious.setText(BTN_PREVIOUS);
        mButtonPrevious.setWidth(400);
        mButtonPrevious.setVisibility(View.INVISIBLE);
        mButtonPrevious.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    showPage(mCurrentPage.getIndex() - 1);
                }
            }
        });
        this.richPushHandlerContainer.addView(this.mButtonPrevious, layoutParams);

        layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        mButtonNext = new Button(getContext());
        mButtonNext.setText(BTN_NEXT_TXT);
        mButtonNext.setWidth(400);
        mButtonNext.setVisibility(View.INVISIBLE);
        mButtonNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    showPage(mCurrentPage.getIndex() + 1);
                }
            }
        });
        this.richPushHandlerContainer.addView(this.mButtonNext, layoutParams);
        addContentView(this.richPushHandlerContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {

        this.webBackView = new LinearLayout(getContext());
        LinearLayout webViewContainer = new LinearLayout(getContext());

        WebView webView = new WebView(getContext());
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebViewClient(new RichPushWebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setUseWideViewPort(true);
        boolean usingPdfRender = false;
        if(this.requestUrl.toLowerCase().endsWith(".pdf")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mImageView = new ImageView(getContext());
                mImageView.setLayoutParams(FILL);
                new RenderPdfTask().execute(new String[] {this.requestUrl});
                usingPdfRender = true;
            } else {
                webView.loadUrl(new StringBuilder(GOOGLE_DOCS_BASE_VIEWER_URL).append(this.requestUrl).toString());
            }
        } else {
            webView.loadUrl(this.requestUrl);
        }
        webView.setLayoutParams(FILL);

        this.webBackView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.webBackView.setBackgroundColor(Color.DKGRAY);
        this.webBackView.setPadding(3, 3, 3, 3);
        this.webBackView.addView(webView);
        this.webBackView.setVisibility(View.INVISIBLE);

        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        Point size = new Point();
        //API14以上
        disp.getSize(size);
        //API14以下
        //int dispWidth = disp.getWidth() / 60;
        int dispWidth = size.x / 60;
        int closeImageWidth = this.closeImage.getDrawable().getIntrinsicWidth();
        webViewContainer.setPadding(dispWidth, closeImageWidth / 2, dispWidth, dispWidth);
        if (usingPdfRender) {
            mImageView.setBackgroundColor(Color.WHITE);
            mImageView.setPadding(3, 3, 3, 3);
            this.mImageView.setVisibility(View.INVISIBLE);
            webViewContainer.addView(this.mImageView);
            NCMBRichPush.this.progressDialog.show();
        } else {
            webViewContainer.addView(this.webBackView);
        }

        this.richPushHandlerContainer.addView(webViewContainer);
    }

    private void createCloseImage() {

        this.closeImage = new ImageView(getContext());

        this.closeImage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                NCMBRichPush.this.cancel();
            }
        });
        Drawable closeDrawable = getContext().getResources().getDrawable(android.R.drawable.btn_dialog);
        this.closeImage.setImageDrawable(closeDrawable);

        this.closeImage.setVisibility(View.INVISIBLE);
    }

    private class RichPushWebViewClient extends WebViewClient {
        private RichPushWebViewClient() {
        }

        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
        }

        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            NCMBRichPush.this.progressDialog.show();
        }

        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            try {
                NCMBRichPush.this.progressDialog.dismiss();
            } catch (IllegalArgumentException localIllegalArgumentException) {
                Log.e("Error", localIllegalArgumentException.toString());
            }

            NCMBRichPush.this.richPushHandlerContainer.setBackgroundColor(0);
            NCMBRichPush.this.webBackView.setVisibility(View.VISIBLE);
            NCMBRichPush.this.closeImage.setVisibility(View.VISIBLE);
        }
    }

    public class RenderPdfTask extends AsyncTask<String, Void, Bitmap> {

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        protected Bitmap doInBackground(String... params) {
            URL url;
            try {
                url = new URL(params[0]);
                String tDir = System.getProperty("java.io.tmpdir");
                String path = tDir + "tmp" + ".pdf";
                File file = new File(path); file.deleteOnExit();
                FileUtils.copyURLToFile(url, file);

                mFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                mPdfRenderer = new PdfRenderer(mFileDescriptor);
                return renderPageZero();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            mImageView.setImageBitmap(bitmap);
            NCMBRichPush.this.progressDialog.dismiss();
            mImageView.setVisibility(View.VISIBLE);
            NCMBRichPush.this.closeImage.setVisibility(View.VISIBLE);
            mButtonPrevious.setVisibility(View.VISIBLE);
            mButtonNext.setVisibility(View.VISIBLE);
            updateUi();
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private Bitmap renderPageZero() {
            // Make sure to close the current page before opening another one.
            if (null != mCurrentPage) {
                mCurrentPage.close();
            }
            // Use `openPage` to open a specific page in PDF.
            mCurrentPage = mPdfRenderer.openPage(0);
            // Important: the destination bitmap must be ARGB (not RGB).
            Bitmap bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(),
                    Bitmap.Config.ARGB_8888);
            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get
            // the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            mCurrentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            // We are ready to show the Bitmap to userbitmap
            return bitmap;
        }
    }

    /**
     * Updates the state of 2 control buttons in response to the current page index.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updateUi() {
        int index = mCurrentPage.getIndex();
        int pageCount = mPdfRenderer.getPageCount();
        mButtonPrevious.setEnabled(0 != index);
        mButtonNext.setEnabled(index + 1 < pageCount);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void showPage(int index) {
        if (mPdfRenderer.getPageCount() <= index) {
            return;
        }
        // Make sure to close the current page before opening another one.
        if (null != mCurrentPage) {
            mCurrentPage.close();
        }
        // Use `openPage` to open a specific page in PDF.
        mCurrentPage = mPdfRenderer.openPage(index);
        // Important: the destination bitmap must be ARGB (not RGB).
        Bitmap bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(),
                Bitmap.Config.ARGB_8888);
        // Here, we render the page onto the Bitmap.
        // To render a portion of the page, use the second and third parameter. Pass nulls to get
        // the default result.
        // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
        mCurrentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        updateUi();
        mImageView.setImageBitmap(bitmap);
    }
}