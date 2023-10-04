package sh.siava.AOSPMods.modpacks.systemui;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static android.graphics.Paint.Style.FILL;
import static android.service.quicksettings.Tile.STATE_ACTIVE;
import static android.service.quicksettings.Tile.STATE_UNAVAILABLE;
import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static sh.siava.AOSPMods.modpacks.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.RemoteException;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;

import com.topjohnwu.superuser.Shell;

import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import sh.siava.AOSPMods.IRootProviderProxy;
import sh.siava.AOSPMods.modpacks.Constants;
import sh.siava.AOSPMods.modpacks.XPLauncher;
import sh.siava.AOSPMods.modpacks.XposedModPack;
import sh.siava.AOSPMods.modpacks.utils.SystemUtils;

@SuppressWarnings("RedundantThrows")
public class ThemeManager_14 extends XposedModPack {
	public static final String listenPackage = Constants.SYSTEM_UI_PACKAGE;
	private static boolean lightQSHeaderEnabled = false;
	private static boolean enablePowerMenuTheme = false;
	private static boolean brightnessThickTrackEnabled = false;
	private boolean isDark;
	private Integer colorInactive = null;

	private final int colorFadedBlack = applyAlpha(0.3f, BLACK); //30% opacity of black color

	private int colorUnavailable;
	private int colorActive;
	private Drawable lightFooterShape = null;
	private int mScrimBehindTint = BLACK;
	private Object unlockedScrimState;

	public ThemeManager_14(Context context) {
		super(context);
		if (!listensTo(context.getPackageName())) return;

		lightFooterShape = getFooterShape(context);
		isDark = isDarkMode();
	}

	@Override
	public void updatePrefs(String... Key) {
		if (Xprefs == null) return;

		enablePowerMenuTheme = Xprefs.getBoolean("enablePowerMenuTheme", false);
		setLightQSHeader(Xprefs.getBoolean("LightQSPanel", false));
		boolean newbrightnessThickTrackEnabled = Xprefs.getBoolean("BSThickTrackOverlay", false);
		if (newbrightnessThickTrackEnabled != brightnessThickTrackEnabled) {
			brightnessThickTrackEnabled = newbrightnessThickTrackEnabled;

			rebuildSysUI(true);
		}

		try {
			if (Key[0].equals("LightQSPanel")) {
				//Application of Light QS usually only needs a screen off/on. but some users get confused. Let's restart systemUI and get it over with
				//This has to happen AFTER overlays are applied. So we do it after update operations are done
				SystemUtils.killSelf();
			}
		} catch (Throwable ignored) {
		}
	}

	public void setLightQSHeader(boolean state) {
		if (lightQSHeaderEnabled != state) {
			lightQSHeaderEnabled = state;

			rebuildSysUI(true);
		}
	}

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
		if (!lpparam.packageName.equals(listenPackage)) return;

		Class<?> QSTileViewImplClass = findClass("com.android.systemui.qs.tileimpl.QSTileViewImpl", lpparam.classLoader);
		Class<?> ScrimControllerClass = findClass("com.android.systemui.statusbar.phone.ScrimController", lpparam.classLoader);
		Class<?> QSPanelControllerClass = findClass("com.android.systemui.qs.QSPanelController", lpparam.classLoader);
		Class<?> ScrimStateEnum = findClass("com.android.systemui.statusbar.phone.ScrimState", lpparam.classLoader);
		Class<?> QSIconViewImplClass = findClass("com.android.systemui.qs.tileimpl.QSIconViewImpl", lpparam.classLoader);
		Class<?> CentralSurfacesImplClass = findClass("com.android.systemui.statusbar.phone.CentralSurfacesImpl", lpparam.classLoader);
		Class<?> GlobalActionsDialogLiteSinglePressActionClass = findClass("com.android.systemui.globalactions.GlobalActionsDialogLite$SinglePressAction", lpparam.classLoader);
		Class<?> GlobalActionsDialogLiteEmergencyActionClass = findClass("com.android.systemui.globalactions.GlobalActionsDialogLite$EmergencyAction", lpparam.classLoader);
		Class<?> GlobalActionsLayoutLiteClass = findClass("com.android.systemui.globalactions.GlobalActionsLayoutLite", lpparam.classLoader);
		Class<?> QSFooterViewClass = findClass("com.android.systemui.qs.QSFooterView", lpparam.classLoader);
		Class<?> TextButtonViewHolderClass = findClass("com.android.systemui.qs.footer.ui.binder.TextButtonViewHolder", lpparam.classLoader);
		Class<?> NumberButtonViewHolderClass = findClass("com.android.systemui.qs.footer.ui.binder.NumberButtonViewHolder", lpparam.classLoader);
		Class<?> BrightnessSliderViewClass = findClass("com.android.systemui.settings.brightness.BrightnessSliderView", lpparam.classLoader);
		Class<?> ShadeCarrierClass = findClass("com.android.systemui.shade.carrier.ShadeCarrier", lpparam.classLoader);
		Class<?> QSCustomizerClass = findClass("com.android.systemui.qs.customize.QSCustomizer", lpparam.classLoader);
		Class<?> BatteryStatusChipClass = findClass("com.android.systemui.statusbar.BatteryStatusChip", lpparam.classLoader);
		Class<?> QSContainerImplClass = findClass("com.android.systemui.qs.QSContainerImpl", lpparam.classLoader);
		Class<?> ShadeHeaderControllerClass = findClassIfExists("com.android.systemui.shade.ShadeHeaderController", lpparam.classLoader);
		Class<?> FooterActionsViewBinderClass = findClass("com.android.systemui.qs.footer.ui.binder.FooterActionsViewBinder", lpparam.classLoader);

		if (ShadeHeaderControllerClass == null)
		{
			ShadeHeaderControllerClass = findClass("com.android.systemui.shade.LargeScreenShadeHeaderController", lpparam.classLoader);
		}

		hookAllConstructors(QSCustomizerClass, new XC_MethodHook() { //QS Customize panel
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled && !isDark) {
					ViewGroup mainView = (ViewGroup) param.thisObject;
					for (int i = 0; i < mainView.getChildCount(); i++) {
						mainView.getChildAt(i).setBackgroundColor(mScrimBehindTint);
					}
				}
			}
		});

		hookAllMethods(ShadeCarrierClass, "updateState", new XC_MethodHook() { //mobile signal icons
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled && !isDark) {
					((ImageView) getObjectField(param.thisObject
							, "mMobileSignal"))
							.setImageTintList(
									ColorStateList.valueOf(BLACK)
							);
				}
			}
		});

		hookAllConstructors(NumberButtonViewHolderClass, new XC_MethodHook() { //QS security footer count circle
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled && !isDark) {
					((ImageView) getObjectField(param.thisObject
							, "newDot"))
							.setColorFilter(BLACK);

					((TextView) getObjectField(param.thisObject
							, "number"))
							.setTextColor(BLACK);
				}
			}
		});

		hookAllConstructors(TextButtonViewHolderClass, new XC_MethodHook() { //QS security footer
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled && !isDark) {
					((ImageView) getObjectField(param.thisObject
							, "chevron"))
							.setColorFilter(BLACK);

					((ImageView) getObjectField(param.thisObject
							, "icon"))
							.setColorFilter(BLACK);

					((ImageView) getObjectField(param.thisObject
							, "newDot"))
							.setColorFilter(BLACK);

					((TextView) getObjectField(param.thisObject
							, "text"))
							.setTextColor(BLACK);
				}
			}
		});

		hookAllMethods(QSFooterViewClass, "onFinishInflate", new XC_MethodHook() { //QS Footer built text row
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled && !isDark) {
					((TextView) getObjectField(param.thisObject
							, "mBuildText"))
							.setTextColor(BLACK);

					((ImageView) getObjectField(param.thisObject
							, "mEditButton"))
							.setColorFilter(BLACK);

					setObjectField(
							getObjectField(
									param.thisObject
									, "mPageIndicator")
							, "mTint"
							, ColorStateList.valueOf(BLACK));
				}
			}
		});

		hookAllMethods(BatteryStatusChipClass, "updateResources", new XC_MethodHook() { //background color of 14's charging chip. Fix for light QS theme situation
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled && !isDark)
					((LinearLayout) getObjectField(param.thisObject, "roundedContainer"))
							.getBackground()
							.setTint(colorInactive);
			}
		});
		hookAllMethods(BrightnessSliderViewClass, "onFinishInflate", new XC_MethodHook() { //brightness slider
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled && !isDark) {
					((LayerDrawable) callMethod(
							getObjectField(param.thisObject, "mSlider")
							, "getProgressDrawable"))
							.findDrawableByLayerId(android.R.id.background)
							.setTint(Color.GRAY); //setting brightness slider background to gray

					((GradientDrawable) getObjectField(param.thisObject
							, "mProgressDrawable"))
							.setColor(colorActive); //progress drawable


					LayerDrawable progress = (LayerDrawable) callMethod(getObjectField(param.thisObject, "mSlider"), "getProgressDrawable");
					DrawableWrapper progressSlider = (DrawableWrapper) progress
							.findDrawableByLayerId(android.R.id.progress);
					LayerDrawable actualProgressSlider = (LayerDrawable) progressSlider.getDrawable();
					@SuppressLint("DiscouragedApi")
					Drawable slider_icon = actualProgressSlider.findDrawableByLayerId(mContext.getResources().getIdentifier("slider_icon", "id", mContext.getPackageName()));
					slider_icon.setTint(WHITE); //progress icon
				}
			}
		});

		unlockedScrimState = Arrays.stream(ScrimStateEnum.getEnumConstants()).filter(c -> c.toString().equals("UNLOCKED")).findFirst().get();

		hookAllMethods(unlockedScrimState.getClass(), "prepare", new XC_MethodHook() { //after brightness adjustment
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (!lightQSHeaderEnabled) return;

				setObjectField(unlockedScrimState, "mBehindTint", mScrimBehindTint);
			}
		});

		hookAllConstructors(QSPanelControllerClass, new XC_MethodHook() { //important to calculate colors specially when material colors change
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				calculateColors();
			}
		});


		hookAllMethods(FooterActionsViewBinderClass, "bind", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled && !isDark) {
					LinearLayout view = (LinearLayout) param.args[0];
					view.setBackgroundColor(mScrimBehindTint);
					view.setElevation(0); //remove elevation shadow
				}
			}
		});

		hookAllMethods(ShadeHeaderControllerClass, "onInit", new XC_MethodHook() { //setting colors for some icons
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				View mView = (View) getObjectField(param.thisObject, "mView");

				Object iconManager = getObjectField(param.thisObject, "iconManager");
				Object batteryIcon = getObjectField(param.thisObject, "batteryIcon");
				Object configurationControllerListener = getObjectField(param.thisObject, "configurationControllerListener");
				hookAllMethods(configurationControllerListener.getClass(), "onConfigChanged", new XC_MethodHook() {
					@SuppressLint("DiscouragedApi")
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						if (!lightQSHeaderEnabled) return;

						int textColor = SettingsLibUtilsProvider.getColorAttrDefaultColor(android.R.attr.textColorPrimary, mContext);

						((TextView) mView.findViewById(mContext.getResources().getIdentifier("clock", "id", mContext.getPackageName()))).setTextColor(textColor);
						((TextView) mView.findViewById(mContext.getResources().getIdentifier("date", "id", mContext.getPackageName()))).setTextColor(textColor);

						callMethod(iconManager, "setTint", textColor);

						for (int i = 1; i <= 3; i++) {
							String id = String.format("carrier%s", i);

							((TextView) getObjectField(mView.findViewById(mContext.getResources().getIdentifier(id, "id", mContext.getPackageName())), "mCarrierText")).setTextColor(textColor);
							((ImageView) getObjectField(mView.findViewById(mContext.getResources().getIdentifier(id, "id", mContext.getPackageName())), "mMobileSignal")).setImageTintList(ColorStateList.valueOf(textColor));
							((ImageView) getObjectField(mView.findViewById(mContext.getResources().getIdentifier(id, "id", mContext.getPackageName())), "mMobileRoaming")).setImageTintList(ColorStateList.valueOf(textColor));
						}

						callMethod(batteryIcon, "updateColors", textColor, textColor, textColor);
					}
				});
			}
		});

		hookAllMethods(QSContainerImplClass, "updateResources", new XC_MethodHook() { //setting colors for more icons
			@SuppressLint("DiscouragedApi")
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (!isDark && lightQSHeaderEnabled) {
					Resources res = mContext.getResources();
					ViewGroup view = (ViewGroup) param.thisObject;

					View settings_button_container = view.findViewById(res.getIdentifier("settings_button_container", "id", mContext.getPackageName()));
					settings_button_container.getBackground().setTint(colorInactive);

					//Power Button on QS Footer
					ViewGroup powerButton = view.findViewById(res.getIdentifier("pm_lite", "id", mContext.getPackageName()));
					((ImageView) powerButton
							.getChildAt(0))
							.setColorFilter(colorInactive, PorterDuff.Mode.SRC_IN);
					powerButton.getBackground().setTint(colorActive);

					ImageView icon = settings_button_container.findViewById(res.getIdentifier("icon", "id", mContext.getPackageName()));
					icon.setColorFilter(BLACK);

					((FrameLayout.LayoutParams)
							((ViewGroup) settings_button_container
									.getParent()
							).getLayoutParams()
					).setMarginEnd(0);


					ViewGroup parent = (ViewGroup) settings_button_container.getParent();
					for (int i = 0; i < 3; i++) //Security + Foreground services containers
					{
						parent.getChildAt(i).setBackground(lightFooterShape.getConstantState().newDrawable().mutate());
					}
				}
			}
		});

		hookAllMethods(QSIconViewImplClass, "updateIcon", new XC_MethodHook() { //setting color for QS tile icons on light theme
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (lightQSHeaderEnabled
						&& !isDark) {

					int color;
					switch (getIntField(param.args[1], "state")) {
						case STATE_ACTIVE:
							color = colorInactive;
							break;
						case STATE_UNAVAILABLE:
							color = colorFadedBlack;
							break;
						default:
							color = BLACK;
							break;
					}

					((ImageView) param.args[0])
							.setImageTintList(
									ColorStateList
											.valueOf(color));
				}
			}
		});

		hookAllConstructors(CentralSurfacesImplClass, new XC_MethodHook() { //SystemUI init
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				rebuildSysUI(true);
			}
		});

		hookAllConstructors(QSTileViewImplClass, new XC_MethodHook() { //setting tile colors in light theme
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (!lightQSHeaderEnabled || isDark) return;

				setObjectField(param.thisObject, "colorActive", colorActive);
				setObjectField(param.thisObject, "colorInactive", colorInactive);
				setObjectField(param.thisObject, "colorUnavailable", colorUnavailable);

				setObjectField(param.thisObject, "colorLabelActive", WHITE);
				setObjectField(param.thisObject, "colorSecondaryLabelActive", WHITE);

				setObjectField(param.thisObject, "colorLabelInactive", BLACK);
				setObjectField(param.thisObject, "colorSecondaryLabelInactive", BLACK);

				setObjectField(param.thisObject, "colorLabelInactive", BLACK);
				setObjectField(param.thisObject, "colorSecondaryLabelInactive", BLACK);
			}
		});

		hookAllMethods(CentralSurfacesImplClass, "updateTheme", new XC_MethodHook() { //required to recalculate colors and overlays when dark is toggled
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				rebuildSysUI(false);
			}
		});

		hookAllMethods(ScrimControllerClass, "updateThemeColors", new XC_MethodHook() { //called when dark toggled or material change.
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				calculateColors();
			}
		});

		hookAllMethods(ScrimControllerClass, "applyState", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (!lightQSHeaderEnabled) return;

				boolean mClipsQsScrim = (boolean) getObjectField(param.thisObject, "mClipsQsScrim");
				if (mClipsQsScrim) {
					setObjectField(param.thisObject, "mBehindTint", mScrimBehindTint);
				}
			}
		});

		//region power menu aka GlobalActions
		hookAllMethods(GlobalActionsLayoutLiteClass, "onLayout", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				if (!enablePowerMenuTheme || isDark) return;

				((View) param.thisObject)
						.findViewById(android.R.id.list)
						.getBackground()
						.setTint(mScrimBehindTint); //Layout background color
			}
		});

		hookAllMethods(GlobalActionsDialogLiteEmergencyActionClass, "create", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (!enablePowerMenuTheme || isDark) return;

				((TextView) ((View) param.getResult())
						.findViewById(android.R.id.message))
						.setTextColor(BLACK); //Emergency Text Color
			}
		});

		hookAllMethods(GlobalActionsDialogLiteSinglePressActionClass, "create", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (!enablePowerMenuTheme || isDark) return;

				View itemView = (View) param.getResult();

				ImageView iconView = itemView.findViewById(android.R.id.icon);

				iconView
						.getDrawable()
						.setTint(colorInactive); //Icon color

				iconView
						.getBackground()
						.setTint(colorActive); //Button Color

				((TextView) itemView
						.findViewById(android.R.id.message))
						.setTextColor(BLACK); //Text Color
			}
		});

		//endregion

	}

	private void rebuildSysUI(boolean force) {
		boolean isCurrentlyDark = isDarkMode();

		if (isCurrentlyDark == isDark && !force) return;

		isDark = isCurrentlyDark;

		calculateColors();

		XPLauncher.enqueueProxyCommand(new XPLauncher.ProxyRunnable() {
			@Override
			public void run(IRootProviderProxy proxy) throws RemoteException {
				Shell.cmd("cmd overlay disable com.google.android.systemui.gxoverlay; cmd overlay enable com.google.android.systemui.gxoverlay").exec();
			}
		});
	}

	@SuppressLint("DiscouragedApi")
	private void calculateColors() { //calculating dual-tone QS scrim color and tile colors
		if (!lightQSHeaderEnabled) return;

		mScrimBehindTint = mContext.getColor(
				isDark
						? android.R.color.system_neutral1_1000
						: android.R.color.system_neutral1_100);

		setObjectField(unlockedScrimState, "mBehindTint", mScrimBehindTint);

		if (!isDark) {
			colorActive = mContext.getColor(android.R.color.system_accent1_600);

			colorInactive = mContext.getColor(android.R.color.system_accent1_10);

			colorUnavailable = applyAlpha(0.3f, colorInactive); //30% opacity of inactive color

			lightFooterShape.setTint(colorInactive);
		}
	}

	private boolean isDarkMode() {
		return SystemUtils.isDarkMode();
	}

	@ColorInt
	public static int applyAlpha(float alpha, int inputColor) {
		alpha *= Color.alpha(inputColor);
		return Color.argb((int) (alpha), Color.red(inputColor), Color.green(inputColor),
				Color.blue(inputColor));
	}

	private static Drawable getFooterShape(Context context) {
		Resources res = context.getResources();
		@SuppressLint("DiscouragedApi") int radius = res.getDimensionPixelSize(res.getIdentifier("qs_security_footer_corner_radius", "dimen", context.getPackageName()));
		float[] radiusF = new float[8];
		for (int i = 0; i < 8; i++) {
			radiusF[i] = radius;
		}
		final ShapeDrawable result = new ShapeDrawable(new RoundRectShape(radiusF, null, null));
		result.getPaint().setStyle(FILL);
		return result;
	}

	@Override
	public boolean listensTo(String packageName) {
		return listenPackage.equals(packageName) && !XPLauncher.isChildProcess;
	}
}