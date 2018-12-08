package com.github.slashmax.aamirror;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

class Unlocker {
    private static final String OUR_PACKAGE = Unlocker.class.getPackage().getName();
    private static final String CAR_ROOT_PACKAGE = "com.google.android.gms.car";
    //    private static final String CAR_PACKAGE = CAR_ROOT_PACKAGE + "#car";
    private static final String CAR_PACKAGES_PATTERN = CAR_ROOT_PACKAGE + "%";
    private static final String TRIGGER_NAME = "after_delete_mirror";

    static List<String> unlock() {
        CommandBuilder builder = new CommandBuilder();
//        builder.disableGmsComponent("phenotype.service.sync.PhenotypeConfigurator");
//        builder.disableGmsComponent("chimera.PersistentDirectBootAwareApiService");

        List<String> result = dropAllTriggers();

        builder.print("Removing black and white lists...");

        builder.sql("DELETE FROM Flags WHERE (name = 'app_black_list' OR name = 'app_white_list') AND packageName LIKE '%s'", CAR_PACKAGES_PATTERN);
        builder.sql("DELETE FROM FlagOverrides WHERE (name = 'app_black_list' OR name = 'app_white_list') AND packageName LIKE '%s'", CAR_PACKAGES_PATTERN);

        builder.print("Adding to white list...");
        String unlockSql = String.format("INSERT OR REPLACE INTO Flags (packageName, version, flagType, partitionId, user, name, stringVal, committed) " +
                        "SELECT DISTINCT '%s', version, 0, 0, '', 'app_white_list', '%s', 1 FROM Flags WHERE packageName LIKE '%s'",
                CAR_ROOT_PACKAGE, OUR_PACKAGE, CAR_PACKAGES_PATTERN);

        builder.sql(unlockSql);

        builder.print("Adding trigger...");
        builder.sql("CREATE TRIGGER %s AFTER DELETE ON Flags BEGIN %s; END", TRIGGER_NAME, unlockSql);

        builder.print("Restarting GMS and AAuto services...");

        builder.stopService("com.google.android.gms");
        builder.stopService("com.google.android.projection.gearhead");

        result.addAll(Shell.run("su", builder.toArray(), null, true));

        return result;
    }

    static boolean isLocked() {
        CommandBuilder builder = new CommandBuilder();
        builder.sql("SELECT COUNT(DISTINCT version) FROM Flags WHERE packageName LIKE '%s'", CAR_PACKAGES_PATTERN);
        builder.sql("SELECT result FROM (SELECT DISTINCT version, CASE WHEN name = 'app_white_list' THEN 1 ELSE 0 END AS result FROM Flags WHERE (name = 'app_black_list' OR name = 'app_white_list') AND stringVal LIKE '%%%s%%' AND packageName = '%s') AS t", OUR_PACKAGE, CAR_ROOT_PACKAGE);
        builder.add("echo \"-\"");
        builder.sql("SELECT COUNT() FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'Flags' AND name = '%s'", TRIGGER_NAME);
        builder.sql("SELECT COUNT() FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'Flags' AND name != '%s'", TRIGGER_NAME);

        List<String> results = Shell.SU.run(builder.toList());

        if (results.size() < 1) {
            return true;
        }
        int count;
        try {
            count = Integer.parseInt(results.get(0));
        } catch (NumberFormatException e) {
            return true;
        }
        int i;
        for (i = 1 ; i < results.size(); i++) {
            String item = results.get(i);
            if (item.equals("0")) {
                return true;
            }
            if (item.equals("1")) {
                count--;
            } else {
                break;
            }
        }
        return count != 0
                || results.size() <= i + 2
                || !results.get(i + 1).equals("1")
                || !results.get(i + 2).equals("0");
    }

    private static List<String> dropAllTriggers() {
        CommandBuilder builder = new CommandBuilder();
        builder.sql("SELECT name FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'Flags'");
        List<String> triggers = Shell.SU.run(builder.toList());

        if (triggers.size() < 1) {
            return triggers;
        }

        builder = new CommandBuilder();
        for (String trigger : triggers) {
            builder.print("Dropping trigger: %s", trigger);
            builder.sql("DROP TRIGGER %s", trigger);
        }

        return Shell.run("su", builder.toArray(), null, true);
    }

    private static class CommandBuilder {
        private List<String> list = new ArrayList<>();

        void add(String command) {
            this.list.add(command);
        }

        void add(String command, Object... args) {
            this.add(String.format(command, args));
        }

        void sql(String sql) {
            this.add(String.format("sqlite3 /data/data/com.google.android.gms/databases/phenotype.db \"%s;\"", sql));
        }

        void sql(String sql, Object... args) {
            this.sql(String.format(sql, args));
        }

        void print(String str) {
            this.add("echo \"%s\"", str);
        }

        void print(String format, Object... args) {
            this.print(String.format(format, args));
        }

        void disableGmsComponent(String name) {
            this.add(String.format("pm disable --user 0 com.google.android.gms/.%s", name));
        }

        void stopService(String name) {
            this.add(String.format("am force-stop %s", name));
        }

        List<String> toList() {
            return this.list;
        }

        String[] toArray() {
            return this.toList().toArray(new String[0]);
        }
    }
}