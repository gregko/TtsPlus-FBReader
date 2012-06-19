package com.hyperionics.fbreader.plugin.tts_plus;

import java.util.*;

import android.content.*;
import android.net.Uri;

import org.geometerplus.android.fbreader.api.PluginApi;

/**
 *  Copyright (C) 2012 Hyperionics Technology LLC <http://www.hyperionics.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


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
