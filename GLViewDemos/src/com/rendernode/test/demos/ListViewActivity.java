package com.rendernode.test.demos;

import android.os.Bundle;

import com.glview.view.View;
import com.glview.view.ViewGroup;
import com.glview.view.LayoutInflater;
import com.rendernode.test.R;

public class ListViewActivity extends BaseActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		View v = LayoutInflater.from(this).inflate(R.layout.gl_layout_list, (ViewGroup) null, false);
		setGLContentView(v);
	}
}
