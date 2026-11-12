package pro.icover.vitrina;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Состав дока: до {@link #MAX} пакетов в заданном порядке. Чистая логика без Android. */
public final class DockModel {
    public static final int MAX = 6;

    private DockModel() {
    }

    /** Первые доступные приложения в порядке списка, до шести. */
    public static List<String> defaults(List<String> installed) {
        return sanitize(installed, installed);
    }

    /** Сохранённый док без удалённых приложений и повторов, не длиннее {@link #MAX}. */
    public static List<String> sanitize(List<String> saved, Collection<String> installed) {
        List<String> out = new ArrayList<>();
        for (String p : saved) {
            if (out.size() == MAX) {
                break;
            }
            if (p != null && !p.isEmpty() && installed.contains(p) && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Добавить в док в конец. Док полон — приложение встаёт вместо последнего
     * (в меню это так и подписано: «В док вместо …»). Уже в доке — без изменений.
     */
    public static List<String> add(List<String> dock, String pkg) {
        List<String> out = new ArrayList<>(dock);
        if (out.contains(pkg)) {
            return out;
        }
        if (out.size() >= MAX) {
            out.set(MAX - 1, pkg);
        } else {
            out.add(pkg);
        }
        return out;
    }

    /** Кого вытеснит {@link #add}: последний, если док полон, иначе null. */
    public static String displacedBy(List<String> dock, String pkg) {
        if (dock.contains(pkg) || dock.size() < MAX) {
            return null;
        }
        return dock.get(MAX - 1);
    }

    public static List<String> remove(List<String> dock, String pkg) {
        List<String> out = new ArrayList<>(dock);
        out.remove(pkg);
        return out;
    }

    /** Сдвинуть на соседнее место: delta = −1 влево, +1 вправо. У края — без изменений. */
    public static List<String> move(List<String> dock, String pkg, int delta) {
        List<String> out = new ArrayList<>(dock);
        int i = out.indexOf(pkg);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= out.size()) {
            return out;
        }
        out.set(i, out.get(j));
        out.set(j, pkg);
        return out;
    }

    /** Кодирование для SharedPreferences: имена пакетов не содержат запятых. */
    public static String encode(List<String> dock) {
        return String.join(",", dock);
    }

    public static List<String> decode(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return out;
        }
        for (String p : s.split(",")) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }
}
