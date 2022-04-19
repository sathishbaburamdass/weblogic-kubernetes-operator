package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.Map;

public class FmwMapping {
    static Map<String, String> productIdMap = new HashMap<>();
    static Map<String, String> domainNameMap = new HashMap<>();
    static Map<String,String>  pipelineStageMap = new HashMap<>();
    static Map<String,String>  parallelStageMap = new HashMap<>();
    static Map<String, String> productDirectoryMap = new HashMap<>();
    /** isDatabaseVersionRequired stores whether a particular product requires database or not */
    static Map<String,Boolean> isDatabaseVersionRequired = new HashMap<>();
    /** Maps added to track products whether they are operator compliant or non operator compliant */
    static Map<String, String> operatorCompliantProductMap = new HashMap<>();
    static Map<String, String> nonOperatorCompliantProductMap = new HashMap<>();
    static Map<String, String> managedServerBaseNames = new HashMap<>();
    static {
        productDirectoryMap.put("WLS", "OracleWebLogic");
        productDirectoryMap.put("WLS-GENERIC", "OracleWebLogic");
        productDirectoryMap.put("WLS-DEVELOPER", "OracleWebLogic");
        productDirectoryMap.put("WLS-INFRA", "OracleFMWInfrastructure");
        productDirectoryMap.put("SOA", "OracleSOASuite");
        productDirectoryMap.put("WCP", "OracleWebCenterPortal");
        productDirectoryMap.put("WC-SITES", "OracleWebCenterSites");
        productDirectoryMap.put("OIG", "OracleIdentityGovernance");
        productDirectoryMap.put("OAM", "OracleAccessManagement");
        productDirectoryMap.put("OUD", "OracleUnifiedDirectory");
        productDirectoryMap.put("OUDSM", "OracleUnifiedDirectorySM");
        productDirectoryMap.put("OID", " OracleInternetDirectory");
        productDirectoryMap.put("WCC", "OracleWebCenterContent");

        productIdMap.put("WLS", "weblogic");
        productIdMap.put("WLS-GENERIC", "weblogic");
        productIdMap.put("WLS-DEVELOPER", "weblogic");
        productIdMap.put("WLS-INFRA", "fmw-infrastructure");
        productIdMap.put("SOA", "soa");
        productIdMap.put("WCP", "wcp");
        productIdMap.put("WC-SITES", "wcsites");
        productIdMap.put("OIG", "oim");
        productIdMap.put("OAM", "access");
        productIdMap.put("OUD", "oud");
        productIdMap.put("OUDSM", "oudsm");
        productIdMap.put("OID", "oid");
        productIdMap.put("WCC", "wcc");

        domainNameMap.put("WLS", "weblogic");
        domainNameMap.put("WLS-GENERIC", "weblogic");
        domainNameMap.put("WLS-DEVELOPER", "weblogic");
        domainNameMap.put("WLS-INFRA", "wlsinfra");
        domainNameMap.put("SOA", "soainfra");
        domainNameMap.put("WCP", "wcpinfra");
        domainNameMap.put("WC-SITES", "wcsites");
        domainNameMap.put("OIG", "oim");
        domainNameMap.put("OAM", "access");
        domainNameMap.put("OUD", "oud");
        domainNameMap.put("OUDSM", "oudsm");
        domainNameMap.put("OID", "oid");
        domainNameMap.put("WCC", "wccinfra");

        isDatabaseVersionRequired.put("WLS", false);
        isDatabaseVersionRequired.put("WLS-GENERIC", false);
        isDatabaseVersionRequired.put("WLS-DEVELOPER", false);
        isDatabaseVersionRequired.put("WLS-INFRA", true);
        isDatabaseVersionRequired.put("SOA", true);
        isDatabaseVersionRequired.put("WCP", true);
        isDatabaseVersionRequired.put("WC-SITES", true);
        isDatabaseVersionRequired.put("OIG", true);
        isDatabaseVersionRequired.put("OAM", true);
        isDatabaseVersionRequired.put("OUD", false);
        isDatabaseVersionRequired.put("OUDSM", false);
        isDatabaseVersionRequired.put("OID", true);
        isDatabaseVersionRequired.put("WCC", true);

        operatorCompliantProductMap.put("WLS", "weblogic");
        operatorCompliantProductMap.put("WLS-GENERIC", "weblogic");
        operatorCompliantProductMap.put("WLS-DEVELOPER", "weblogic");
        operatorCompliantProductMap.put("WLS-INFRA", "fmw-infrastructure");
        operatorCompliantProductMap.put("SOA", "soa");
        operatorCompliantProductMap.put("WCP", "wcp");
        operatorCompliantProductMap.put("WC-SITES", "wcsites");
        operatorCompliantProductMap.put("OIG", "oim");
        operatorCompliantProductMap.put("OAM", "access");
        operatorCompliantProductMap.put("WCC", "wcc");

        nonOperatorCompliantProductMap.put("OUD", "oud");
        nonOperatorCompliantProductMap.put("OUDSM", "oudsm");
        nonOperatorCompliantProductMap.put("OID", "oid");

        managedServerBaseNames.put("SOA", "soa-server");
        managedServerBaseNames.put("WCC", "ucm-server");
    }
    public static <K,V> K getProdName(Map<K,V> map,V val){
        return map.entrySet().stream()
                .filter(entry->val.equals(entry.getValue()))
                .findFirst().map(Map.Entry::getKey)
                .orElse(null);
    }
}
