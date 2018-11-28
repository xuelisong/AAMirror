package com.github.slashmax.aamirror;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

class Unlocker {
    private static final String OUR_PACKAGE = Unlocker.class.getPackage().getName();
    private static final String CAR_ROOT_PACKAGE = "com.google.android.gms.car";
    private static final String CAR_PACKAGE = CAR_ROOT_PACKAGE + "#car";
    private static final String CAR_PACKAGES_PATTERN = CAR_ROOT_PACKAGE + "%";
    private static final String TRIGGER_NAME = "after_delete_mirror";

    static List<String> unlock() {
        CommandBuilder builder = new CommandBuilder();
//        builder.disableGmsComponent("phenotype.service.sync.PhenotypeConfigurator");
//        builder.disableGmsComponent("chimera.PersistentDirectBootAwareApiService");

        String unlockSql = String.format("INSERT OR REPLACE INTO Flags (packageName, version, flagType, partitionId, user, name, stringVal, committed) " +
                        "SELECT DISTINCT \"%s\", version, 0, 0, \"\", \"app_white_list\", \"%s\", 1 FROM Flags WHERE packageName = \"%s\"",
                CAR_ROOT_PACKAGE, OUR_PACKAGE, CAR_PACKAGE);

        builder.sql("DROP TRIGGER %s", TRIGGER_NAME);
        builder.sql("CREATE TRIGGER %s AFTER DELETE ON Flags BEGIN %s END", TRIGGER_NAME, unlockSql);

        builder.sql("DELETE FROM Flags WHERE (name = \"app_black_list\" OR name = \"app_white_list\") AND packageName LIKE \"%s\"", CAR_PACKAGES_PATTERN);
        builder.sql("DELETE FROM FlagOverrides WHERE (name = \"app_black_list\" OR name = \"app_white_list\") AND packageName LIKE \"%s\"", CAR_PACKAGES_PATTERN);

        builder.sql(unlockSql);

//        builder.stopService("com.google.android.gms");
//        builder.stopService("com.google.android.projection.gearhead");

        return Shell.SU.run(builder.toList());
    }

    static Boolean isLocked() {
        CommandBuilder builder = new CommandBuilder();
        builder.sql("SELECT COUNT(DISTINCT version) FROM Flags WHERE packageName = \"%s\"", CAR_PACKAGE);
        builder.sql("SELECT result FROM (SELECT DISTINCT version, CASE WHEN name = \"app_white_list\" THEN 1 ELSE 0 END AS result FROM Flags WHERE (name = \"app_black_list\" OR name = \"app_white_list\") AND stringVal LIKE \"%%%s%%\" AND packageName = \"%s\") AS t", OUR_PACKAGE, CAR_ROOT_PACKAGE);

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
        for (int i = 1; i < results.size(); i++) {
            String item = results.get(i);
            if (item.equals("0")) {
                return true;
            }
            if (item.equals("1")) {
                count--;
            } else {
                return true;
            }
        }
        return count != 0;
    }

    private static class CommandBuilder {
        private List<String> list = new ArrayList<>();

        void add(String command, String... args) {
            this.list.add(String.format(command, (Object[]) args));
        }

        void sql(String sql, String... args) {
            this.add(String.format("sqlite3 /data/data/com.google.android.gms/databases/phenotype.db '%s;'", sql), args);
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
   }
}