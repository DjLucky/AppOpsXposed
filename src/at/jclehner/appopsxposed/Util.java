/*
 * AppOpsXposed - AppOps for Android 4.3+
 * Copyright (C) 2013 Joseph C. Lehner
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of  MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.jclehner.appopsxposed;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import android.content.res.XModuleResources;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public final class Util
{
	private static final boolean DEBUG = true;

	public static XModuleResources settingsRes;
	public static XModuleResources modRes;
	public static XSharedPreferences modPrefs;

	public static int getSettingsIdentifier(String name) {
		return settingsRes.getIdentifier(name, null, AppOpsXposed.SETTINGS_PACKAGE);
	}

	public static String getSettingsString(int resId) {
		return settingsRes.getString(resId);
	}

	public static String getModString(int resId) {
		return modRes.getString(resId);
	}

	public static void debug(String message)
	{
		if(!DEBUG)
			return;

		XposedBridge.log(message);
	}

	public static void debug(Throwable t)
	{
		if(!DEBUG)
			return;

		XposedBridge.log(t);
	}

	public static boolean containsManufacturer(String str) {
		return Build.MANUFACTURER.toLowerCase().contains(str.toLowerCase());
	}

	public static String[] appendToStringArray(String[] array, String str)
	{
		final ArrayList<String> list = new ArrayList<String>(Arrays.asList(array));
		list.add(str);

		final String[] newArray = new String[list.size()];
		return list.toArray(newArray);
	}

	public static String getSystemProperty(String key, String defValue)
	{
		try
		{
			final Method m = Class.forName("android.os.SystemProperties").
					getMethod("get", String.class, String.class);

			return (String) m.invoke(null, key, defValue);
		}
		catch(ReflectiveOperationException e)
		{
			e.printStackTrace();
			return defValue;
		}
	}

	public static void dumpViewHierarchy(View v)
	{
		debug("dumpViewHierarchy: ");
		dumpViewHierarchyInternal(v, 0);
	}

	private static void dumpViewHierarchyInternal(View view, int level)
	{
		debug(pad(2 * level) + view);

		if(view instanceof ViewGroup)
		{
			final ViewGroup vg = (ViewGroup) view;
			for(int i = 0; i != vg.getChildCount(); ++i)
				dumpViewHierarchyInternal(vg.getChildAt(i), level + 1);
		}
	}

	private static String pad(int length)
	{
		final StringBuilder sb = new StringBuilder(length);
		for(int i = 0; i != length; ++i)
			sb.append(' ');

		return sb.toString();
	}

	public static void findAndHookMethodRecursive(String className, ClassLoader classLoader,
			String methodName, Object... parameterTypesAndCallback) throws Throwable
	{
		findAndHookMethodRecursive(classLoader.loadClass(className), methodName, parameterTypesAndCallback);
	}

	// Returning Object here as the XC_MethodHook.Unhook in the method signature
	// *may* be the cause for VerifyErrors on some ROMs...
	public static Object findAndHookMethodRecursive(Class<?> clazz,
			String methodName, Object... parameterTypesAndCallback) throws Throwable
	{
		final Object callback = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
		if(callback instanceof XC_MethodHookRecursive)
		{
			XC_MethodHookRecursive hook = (XC_MethodHookRecursive) callback;
			if(!hook.hasTargetClass())
				hook.setTargetClass(clazz);
		}
		else
			throw new IllegalArgumentException("Callback must extend XC_MethodHookRecursive");

		try
		{
			return findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
		}
		catch(NoSuchMethodError e)
		{
			final Class<?> superClass = clazz.getSuperclass();
			if(superClass == null)
			{
				// We can't just rethrow the caught exception since the message would not be
				// meaningful (clazz is Object.class at this point).
				final Class<?> targetClass = ((XC_MethodHookRecursive) callback).getTargetClass();
				throw new NoSuchMethodError(targetClass.getName() + "." + methodName);
			}

			debug("findAndHookMethodRecursive: trying " + superClass + "." + methodName);
			return findAndHookMethodRecursive(superClass, methodName, parameterTypesAndCallback);
		}
	}

	public static class XC_MethodHookRecursive extends XC_MethodHook
	{
		private Class<?> mClass = null;

		/* package */ void setTargetClass(Class<?> clazz) {
			mClass = clazz;
		}

		/* package */ void setTargetClass(String className, ClassLoader classLoader)
		{
			try
			{
				mClass = classLoader.loadClass(className);
			}
			catch(ClassNotFoundException e)
			{
				throw new IllegalArgumentException(e);
			}
		}

		/* package */ Class<?> getTargetClass() {
			return mClass;
		}

		/* package */ boolean hasTargetClass() {
			return mClass != null;
		}

		@Override
		protected final void beforeHookedMethod(MethodHookParam param) throws Throwable
		{
			if(isValidThisObject(param))
				onBeforeHookedMethod(param);
			else
				debug("Skipping beforeHookedMethod with this=" + param.thisObject.getClass());
		}

		@Override
		protected final void afterHookedMethod(MethodHookParam param) throws Throwable
		{
			if(isValidThisObject(param))
				onAfterHookedMethod(param);
			else
				debug("Skipping afterHookedMethod with this=" + param.thisObject.getClass());
		}

		protected void onBeforeHookedMethod(MethodHookParam param) throws Throwable {}
		protected void onAfterHookedMethod(MethodHookParam param) throws Throwable {}

		private boolean isValidThisObject(MethodHookParam param) {
			return mClass == null || mClass.isInstance(param.thisObject);
		}
	}

	private Util() {}
}
