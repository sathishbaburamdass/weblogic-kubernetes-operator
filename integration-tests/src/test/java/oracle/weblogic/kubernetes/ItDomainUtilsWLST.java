// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;

/**
 * Util Class to deploy domain using fmw sample repo
 */

//find product
//customize rcu
//find product id
@DisplayName("Create given domain using sample repo")
@IntegrationTest
class ItDomainUtilsWLST {

  private static final String RCUSCHEMAPREFIX = TestConstants.FMW_DOMAIN_TYPE + "domainpv";
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static String connectionURL = "";
  private static String dbImage = "";
  private static String productImage = "";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private static final String domainUid = TestConstants.FMW_DOMAIN_TYPE + "infra";
  private static final String wlSecretName = domainUid + "-weblogic-credentials";
  private static final String rcuSecretName = domainUid + "-rcu-credentials";
  private static final String workSpacePath = "/home/opc/intg-test/workspace/fmwsamples/";
  private static final String workSpaceBasePath = "/home/opc/intg-test/workspace/";
  private static final long TIMESTAMP = System.currentTimeMillis();
  private static final String domainNS = TestConstants.FMW_DOMAIN_TYPE + "-ns-" + TIMESTAMP;
  private static final String operatorNS = "opt-ns-" + TIMESTAMP;
  private static final String OPT_VERSION = TestConstants.OPERATOR_VERSION;
  public static final String prodID = FmwMapping.getProdName(FmwMapping.productIdMap,TestConstants.FMW_DOMAIN_TYPE);
  private static final String prodDirectory = FmwMapping.productDirectoryMap.get(prodID);


  public static void deployDomainUsingSampleRepo() throws IOException {
    logger = getLogger();
    System.out.println("****----Inside Init All : Domain deployment via Sample Scripts****----");
    System.out.println("IS UPPERSTACK : "+ TestConstants.IS_UPPERSTACK);

    //create ns & cluster bindings
    copyRepos();
    prepareENV();

    //update create-pv-pvc-inputs.yaml
    pv_pvc_util();

    //install operator
    installOperator();

    //prepare db
    prepareDB();
    prepareRCU();

    //create domain
    domain_yaml_util();

    createDomain();


    try {
      MINUTES.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public static void pv_pvc_util() throws IOException {
    String nfs_folder = TestConstants.FMW_DOMAIN_TYPE + "-store-" + TIMESTAMP;
    nfs_folder = "/scratch/u01/DockerVolume/domains/" + nfs_folder;
    //mkdir in nfs domain share
    new Command().withParams(new CommandParams()
            .command("mkdir "+nfs_folder)).execute();
    new Command().withParams(new CommandParams()
            .command("chmod -R 777 "+nfs_folder)).execute();

    String pv_pvc = "---\n" +
            "version: create-"+domainUid+"-pv-pvc-inputs-v1\n" +
            "baseName: domain\n" +
            "domainUID: "+domainUid+"\n" +
            "namespace: "+domainNS+"\n" +
            "weblogicDomainStorageType: HOST_PATH\n" +
            "weblogicDomainStoragePath: "+ nfs_folder + "\n" +
            "weblogicDomainStorageReclaimPolicy: Recycle\n" +
            "weblogicDomainStorageSize: 10Gi";
    File file=new File(workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml");
    DataOutputStream outstream= new DataOutputStream(new FileOutputStream(file,false));
    outstream.write(pv_pvc.getBytes());
    outstream.close();
    String pvName = domainUid+"-domain-pv.yaml";
    String pvcName = domainUid+"-domain-pvc.yaml";

    new Command().withParams(new CommandParams()
            .command("cd "+workSpacePath+" && ./"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-pv-pvc/create-pv-pvc.sh -i "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml -o script-output-directory && cp script-output-directory/pv-pvcs/"+pvName+" . && cp script-output-directory/pv-pvcs/"+pvcName+" .")).execute();

    new Command().withParams(new CommandParams()
            .command("cd /home/opc/intg-test/workspace/fmwsamples && kubectl apply -f "+pvName+" && kubectl apply -f "+pvcName)).execute();

  }

  public static void domain_yaml_util() throws IOException{
    logger.info("---Manipulate Domain Yaml File---");
    File file=new File(workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain/domain-home-on-pv/create-domain-inputs.yaml");
    BufferedReader reader;
    FileWriter writer;
    String content = "";
    //try{
      reader = new BufferedReader(new FileReader(file));
      String line = reader.readLine();

      while (line != null)
      {
        content = content + line + System.lineSeparator();
        line = reader.readLine();
      }

      logger.info(content);
      //start - find and replace domain yaml values
      List<String> domainYamlOrgiValue = Files.readAllLines((Paths.get(workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain/domain-home-on-pv/create-domain-inputs.yaml")));

      Map<String,String> replaceDomainYamlMap = new HashMap<>();
      replaceDomainYamlMap.put("rcuCredentialsSecret: ","rcuCredentialsSecret: "+rcuSecretName);
      replaceDomainYamlMap.put("weblogicCredentialsSecretName: ","weblogicCredentialsSecretName: "+wlSecretName);
      replaceDomainYamlMap.put("namespace: ","namespace: "+domainNS);
      replaceDomainYamlMap.put("initialManagedServerReplicas: ","initialManagedServerReplicas: 2");
      replaceDomainYamlMap.put("rcuDatabaseURL: ","rcuDatabaseURL: "+dbUrl);
      replaceDomainYamlMap.put("persistentVolumeClaimName: ","persistentVolumeClaimName: "+domainUid+"-domain-pvc");
      replaceDomainYamlMap.put("image: ","image: "+productImage);

      Pattern pattern;
      Matcher matcher;
      for(Map.Entry<String,String> e : replaceDomainYamlMap.entrySet()){
        String domainYamlProp = e.getKey();
        pattern = Pattern.compile(domainYamlProp+"?");

        for(String domainYamlByLine : domainYamlOrgiValue){
          matcher = pattern.matcher(domainYamlByLine);
          if(matcher.find()){
            content = content.replace(domainYamlByLine,e.getValue());
          }

        }
      }
      //end - find and replace domain yaml values
      logger.info("--End Domain Yaml File Manipulation--");
      logger.info(content);
      writer = new FileWriter(file);
      writer.write(content);
      reader.close();
      writer.close();
//    }catch(IOException e){
//      e.printStackTrace();
//    }
  }

  public static void copyRepos(){
    new Command()
            .withParams(new CommandParams()
                    .command("rm -rf /home/opc/intg-test/workspace && mkdir -p /home/opc/intg-test/workspace/FMW-DockerImages && mkdir -p /home/opc/intg-test/workspace/weblogic-kubernetes-operator && mkdir -p /home/opc/intg-test/workspace/k8spipeline && chmod -R 777 /home/opc/intg-test/workspace"))
            .execute();

    new Command()
            .withParams(new CommandParams()
                    .command("GIT_SSH_COMMAND='ssh -i /home/opc/intg-test/id_rsa_github -o IdentitiesOnly=yes' git clone git@orahub.oci.oraclecorp.com:paascicd/FMW-DockerImages.git /home/opc/intg-test/workspace/FMW-DockerImages"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("GIT_SSH_COMMAND='ssh -i /home/opc/intg-test/id_rsa_github -o IdentitiesOnly=yes' git clone git@orahub.oci.oraclecorp.com:fmw-platform-qa/fmw-k8s-pipeline.git /home/opc/intg-test/workspace/k8spipeline"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("git clone https://github.com/oracle/weblogic-kubernetes-operator.git /home/opc/intg-test/workspace/weblogic-kubernetes-operator"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("cd /home/opc/intg-test/workspace && mv -f FMW-DockerImages fmwsamples_bkup && mkdir /home/opc/intg-test/workspace/fmwsamples && cd fmwsamples && mkdir -p "+prodDirectory+"/kubernetes/"+OPT_VERSION+""))
            .execute();
    new Command().withParams(new CommandParams()
            .command("cd /home/opc/intg-test/workspace/fmwsamples && cp -rf /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/README.md /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/charts /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/common /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-kubernetes-secrets /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-oracle-db-service /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-rcu-credentials /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-rcu-schema /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-credentials /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-pv-pvc /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/delete-domain /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/domain-lifecycle /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/elasticsearch-and-kibana /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/imagetool-scripts /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/logging-services /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/monitoring-service /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/rest /home/opc/intg-test/workspace/fmwsamples_bkup/"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/scaling "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/")).execute();
  }
  
  public static void prepareENV(){
    new Command().withParams(new CommandParams()
            .command("kubectl create ns "+operatorNS+" && kubectl create ns "+domainNS+" && kubectl create clusterrolebinding crb-default-sa-"+operatorNS+" --clusterrole=cluster-admin --serviceaccount="+operatorNS+":default")).execute();
    connectionURL = "oracledb."+domainNS+":1521/oracledbpdb.us.oracle.com";
    dbImage = "container-registry.oracle.com/database/enterprise:12.2.0.1-slim";
    productImage = TestConstants.FMWINFRA_IMAGE_NAME+":"+TestConstants.FMWINFRA_IMAGE_TAG;
  }

  public static void prepareDB(){

    File file=new File(workSpaceBasePath+"/k8spipeline/kubernetes/framework/db/oracle-db.yaml");
    BufferedReader reader = null;
    FileWriter writer = null;
    String content = "";
    try{
      reader = new BufferedReader(new FileReader(file));
      String line = reader.readLine();

      while (line != null)
      {
        content = content + line + System.lineSeparator();
        line = reader.readLine();
      }
      content = content.replace("%DB_NAME%","oracledb");
      content = content.replace("%DB_IMAGE%",dbImage);
      content = content.replace("%DB_PASSWORD%","Oradoc_db1");
      content = content.replace("%DB_NAMESPACE%",domainNS);
      content = content.replace("%DB_SECRET%","regcred");
      content = content.replace("%DB_PDB_NAME%","oracledbpdb");
      content = content.replace("%DB_DOMAIN_NAME%","us.oracle.com");
      content = content.replace("%DB_IF_READY_PRINT_DONE_OR_COMPLETE%","Done ! The database is ready for use .");
      content = content.replace("%DB_LOG_LOCATION%","/home/oracle/setup/log/setupDB.log");
      writer = new FileWriter(file);
      writer.write(content);
      reader.close();
      writer.close();
    }catch(IOException e){
      e.printStackTrace();
    }

    new Command().withParams(new CommandParams()
            .command("cd "+workSpaceBasePath+"/k8spipeline/kubernetes/framework/db/ && kubectl apply -f oracle-db.yaml -n "+domainNS)).execute();
    new Command().withParams(new CommandParams()
            .command("while [[ $(kubectl get pods oracledb-0 -n "+domainNS+" -o 'jsonpath={..status.conditions[?(@.type==\"Ready\")].status}') != \"True\" ]]; do echo \"waiting for DB pod to be ready\" && sleep 10; done")).execute();

    try {
      SECONDS.sleep(10);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public static void prepareRCU(){
    //create weblogic & rcu creds
    new Command().withParams(new CommandParams()
            .command("cd "+workSpacePath+" && "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-credentials/create-weblogic-credentials.sh -u weblogic -p welcome1 -n "+domainNS+" -d "+domainUid+" && "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-rcu-credentials/create-rcu-credentials.sh -u "+domainUid+" -p Welcome1 -a sys -q Oradoc_db1 -d "+domainUid+" -n "+domainNS)).execute();
    //prepare rcu based on the product
    if(TestConstants.FMW_DOMAIN_TYPE.matches("soa")){
      new Command().withParams(new CommandParams()
              .command("cd "+workSpacePath+" && ./"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-rcu-schema/create-rcu-schema.sh -s "+domainUid+" -t "+TestConstants.FMW_DOMAIN_TYPE+" -d "+connectionURL+" -i "+productImage+" -n "+domainNS+" -q Oradoc_db1 -r Welcome1 -c SOA_PROFILE_TYPE=SMALL,HEALTHCARE_INTEGRATION=NO -l 1000")).execute();
    }if(TestConstants.FMW_DOMAIN_TYPE.matches("oig|wcp|oam")){
      new Command().withParams(new CommandParams()
              .command("cd "+workSpacePath+" && ./"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-rcu-schema/create-rcu-schema.sh -s "+domainUid+" -t "+TestConstants.FMW_DOMAIN_TYPE+" -d "+connectionURL+" -i "+productImage+" -n "+domainNS+" -q Oradoc_db1 -r Welcome1 -l 2000")).execute();
    }else if(TestConstants.FMW_DOMAIN_TYPE.matches("wcc")){
      // cd workSpaceBasePath ,  k8spipeline/kubernetes/framework/db/rcu/fmwk8s-rcu-configmap.yaml
      File file=new File(workSpaceBasePath+"/k8spipeline/kubernetes/framework/db/rcu/fmwk8s-rcu-configmap.yaml");
      BufferedReader reader = null;
      FileWriter writer = null;
      String content = "";
      try{
        reader = new BufferedReader(new FileReader(file));
        String line = reader.readLine();

        while (line != null)
        {
          content = content + line + System.lineSeparator();
          line = reader.readLine();
        }
        content = content.replace("%CONNECTION_STRING%",connectionURL);
        content = content.replace("%RCUPREFIX%",domainUid);
        content = content.replace("%SYS_PASSWORD%","Oradoc_db1");
        content = content.replace("%PASSWORD%","Welcome1");
        content = content.replace("%PRODUCT_ID%",TestConstants.FMW_DOMAIN_TYPE);
        writer = new FileWriter(file);
        writer.write(content);
        reader.close();
        writer.close();
      }catch(IOException e){
        e.printStackTrace();
      }

      file=new File(workSpaceBasePath+"/k8spipeline/kubernetes/framework/db/rcu/fmwk8s-rcu-pod.yaml");
      reader = null;
      writer = null;
      content = "";
      try{
        reader = new BufferedReader(new FileReader(file));
        String line = reader.readLine();

        while (line != null)
        {
          content = content + line + System.lineSeparator();
          line = reader.readLine();
        }
        content = content.replace("%DB_SECRET%","phxregcred");
        content = content.replace("%PRODUCT_ID%",TestConstants.FMW_DOMAIN_TYPE);
        content = content.replace("%PRODUCT_IMAGE%",productImage);
        writer = new FileWriter(file);
        writer.write(content);
        reader.close();
        writer.close();
      }catch(IOException e){
        e.printStackTrace();
      }

      new Command().withParams(new CommandParams()
              .command("cd "+workSpaceBasePath+"/k8spipeline/kubernetes/framework/db/rcu/ && kubectl apply -f fmwk8s-rcu-configmap.yaml -n "+domainNS+" && kubectl apply -f fmwk8s-rcu-pod.yaml -n "+domainNS)).execute();
    }

  }

  public static void findOperatorVerFromYaml(){

  }
  public static void installOperator(){
    new Command().withParams(new CommandParams()
            .command("helm install op-intg-test "+workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/charts/weblogic-operator --namespace "+operatorNS+" --set serviceAccount=default --set 'domainNamespaces={}' --set image=ghcr.io/oracle/weblogic-kubernetes-operator:"+OPT_VERSION+" --wait")).execute();

  }

  public static void createDomain(){
    new Command().withParams(new CommandParams()
            .command("cd "+workSpacePath+" && ./"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain/domain-home-on-pv/create-domain.sh -i  "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain/domain-home-on-pv/create-domain-inputs.yaml -o script-output-domain-directory")).execute();
    new Command().withParams(new CommandParams()
            .command("helm upgrade op-intg-test "+workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/charts/weblogic-operator --namespace "+operatorNS+" --reuse-values --set 'domainNamespaces={"+domainNS+"}' --wait")).execute();
    new Command().withParams(new CommandParams()
            .command("cd "+workSpacePath+"script-output-domain-directory/weblogic-domains/"+domainUid+"/ && kubectl apply -f domain.yaml -n "+domainNS)).execute();

  }
}