package com.luoyunxi.storehook;


import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;



public class HookInit implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"android".equals(lpparam.packageName)) return;

        XposedBridge.log("[Store Hook]: Loaded in system_server");

        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.android.server.wm.ActivityTaskManagerService", lpparam.classLoader);

            for (Method method : cls.getDeclaredMethods()) {
                if (!method.getName().equals("startActivity")) continue;
                if (!Modifier.isPublic(method.getModifiers())) continue;

                Class<?>[] paramTypes = method.getParameterTypes();
                int intentIndex = -1;
                for (int i = 0; i < paramTypes.length; i++) {
                    if (Intent.class.equals(paramTypes[i])) {
                        intentIndex = i;
                        break;
                    }
                }

                if (intentIndex == -1) continue;

                final int index = intentIndex;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Intent intent = (Intent) param.args[index];
                            if (intent == null) return;

                            Uri data = intent.getData();
                            if (!Intent.ACTION_VIEW.equals(intent.getAction()) || data == null)
                                return;


                            String scheme = data.getScheme();
                            String id = data.getQueryParameter("id");

                            // 替换 mimarket:// → market://

                            if ("mimarket".equals(scheme) && id != null) {
                                Intent newIntent = new Intent(Intent.ACTION_VIEW);
                                newIntent.setData(Uri.parse("market://details?id=" + id));
                                newIntent.setFlags(intent.getFlags());
                                newIntent.putExtras(intent);
                                intent = newIntent;

                                XposedBridge.log("[Store Hook]: Replaced mimarket:// with market:// for id=" + id);
                            }

                            // 如果是 market://details?id=...
                            if ("market".equals(intent.getData().getScheme())
                                    && "details".equals(intent.getData().getHost())
                                    && intent.getData().getQueryParameter("id") != null) {

                                // 移除指定包名
                                if (intent.getPackage() != null) {
                                    XposedBridge.log("[Store Hook]: Removed package=" + intent.getPackage());
                                    intent.setPackage(null);
                                }

                                // 强制 chooser
                                Intent chooser = Intent.createChooser(intent, "选择应用打开");
                                param.args[index] = chooser;

                                XposedBridge.log("[Store Hook]: Forced chooser for market://details intent");
                            }

                        } catch (Throwable t) {
                            XposedBridge.log("[Store Hook]: Error - " + Log.getStackTraceString(t));
                        }
                    }
                });

                XposedBridge.log("[Store Hook]: Hooked method: " + method);
                break;
            }

        } catch (Throwable t) {
            XposedBridge.log("[Store Hook]: Failed to hook - " + Log.getStackTraceString(t));
        }
    }
}
