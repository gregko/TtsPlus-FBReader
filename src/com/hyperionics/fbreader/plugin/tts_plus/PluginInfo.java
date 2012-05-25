
package com.hyperionics.fbreader.plugin.tts_plus;

import java.util.*;

import android.content.*;
import android.net.Uri;

import org.geometerplus.android.fbreader.api.PluginApi;

public class PluginInfo extends PluginApi.PluginInfo {
	@Override
	protected List<PluginApi.ActionInfo> implementedActions(Context context) {
		return Collections.<PluginApi.ActionInfo>singletonList(new PluginApi.MenuActionInfo(
			Uri.parse("http://hyperionics.com/plugin/tts_plus/speak"),
			context.getText(R.string.speak_menu_item).toString(),
			Integer.MAX_VALUE
		));
	}
}
