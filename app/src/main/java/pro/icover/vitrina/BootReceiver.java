package pro.icover.vitrina;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * После загрузки открыть «Витрину».
 *
 * В прошивке проектора «Домой» обрабатывает прокладка вендора com.jm.jmlauncher (категория HOME.JM).
 * При первом запуске после загрузки она показывает заставку «SMART PROJECTOR» и ждёт родной лаунчер
 * com.master.t972; если он отключён (так «Витрина» становится домашним экраном), заставка висит до
 * первого нажатия «Домой» (проверено на устройстве 2026-11-09). Поэтому открываемся сами — только когда
 * «Витрина» включена как домашний экран (alias .Home), иначе ничего не делаем. Повторный старт по второму
 * событию безвреден: активность singleTask, просто остаётся на месте.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }
        ComponentName home = new ComponentName(ctx, ctx.getPackageName() + ".Home");
        if (ctx.getPackageManager().getComponentEnabledSetting(home)
                != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return;
        }
        Intent start = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setComponent(home)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(start);
        } catch (RuntimeException e) {
            // Система может запретить старт из фона — тогда остаётся «Домой» на пульте.
        }
    }
}
