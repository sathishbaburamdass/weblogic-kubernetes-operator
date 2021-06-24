// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
//import java.util.logging.Logger;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
//import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

public class OciDbUtils {

  //public static final String JDBC_URL_PREFIX = "jdbc:oracle:thin:@//";
  public static final String JDBC_URL_PREFIX = "jdbc:oracle:thin:@";
  //public static final String CDB_DEFAULT_URL = "phx3187e3f.subnet5ad3phx.devweblogicphx.oraclevcn.com:1521:"
  //    + "db195.subnet5ad3phx.devweblogicphx.oraclevcn.comn";
  public static final String CDB_DEFAULT_URL = "phx3187e3f.subnet5ad3phx.devweblogicphx.oraclevcn.com:1521:"
      + "db195";
  public static final String PDB_DEFAULT_URL = "phx3187e3f.subnet5ad3phx.devweblogicphx.oraclevcn.com:1521/";

  public static final String CDB_DEFAULT_JDBC_URL = JDBC_URL_PREFIX + CDB_DEFAULT_URL;
  public static final String CDB_DEFAULT_USERNAME = "sys as sysdba";
  //private static final char[] CDB_DEFAULT_PASSWORD_BASE64_ENCODED =
  //    encodePassword('w', 'e', 'l', 'c', 'o', 'm', 'e', '1');
  private static final String CDB_DEFAULT_PASSWORD_BASE64_ENCODED = "welcome1";
  //public static final String CDB_DEFAULT_JDBC_DRIVER_CLASS_NAME = "oracle.jdbc.driver.OracleDriver";
  public static final String CDB_DEFAULT_JDBC_DRIVER_CLASS_NAME = "oracle.jdbc.OracleDriver"; //TODO
  private static final String VERIFY_CDB_SQL = "select cdb from v$database";
  private static final String DATABASE_IS_CDB = "YES";
  private static final String PDB_NAME_TOKEN = "$pdbName$";
  private static final String CREATE_PDB_SQL = join("", "create pluggable database ", PDB_NAME_TOKEN,
          " admin user admin identified by admin file_name_convert = ('pdbseed', '", PDB_NAME_TOKEN, "')");

  private static final String OPEN_PDB_SQL = join("alter pluggable database ", PDB_NAME_TOKEN, " open");
  private static final String PDB_STATUS_SQL = "select OPEN_MODE from v$pdbs WHERE NAME = upper(?)";
  private static final String CLOSE_PDB_SQL = join("alter pluggable database ", PDB_NAME_TOKEN, " close");
  private static final String DROP_PDB_SQL = join("drop pluggable database ", PDB_NAME_TOKEN, " INCLUDING DATAFILES");
  //private static final String SELECT_OLD_PDBS_SQL = "select name, open_mode from v$pdbs
  //   where open_time < (sysdate - interval '2' hour) and length(name) = 30";

  private static final String PDB_STATUS_MOUNTED = "MOUNTED";
  private static final String PDB_STATUS_READ_WRITE = "READ WRITE";
  private static final String PDB_STATUS_NONE = "NONE";

  //  suffix 'us.oracle.com'?

  private static final String PDB_SERVICE_NAME_SUFFIX = ".subnet5ad3phx.devweblogicphx.oraclevcn.com";

  public static final String RCU_CONNECT_STRING_KEY = "rcuConnectString";
  public static final String JDBC_URL_KEY = "jdbcUrl";

  private static LoggingFacade logger = getLogger();

  private String jdbcUrl;
  private String username;
  //private char[] password;
  private String password;
  private String dbDriver;

  /**
   * Utility class to handle all PDB/Database operations for tests that require a database.
   */
  public OciDbUtils() {
    jdbcUrl = CDB_DEFAULT_JDBC_URL;
    username = CDB_DEFAULT_USERNAME;
    //password = CDB_DEFAULT_PASSWORD_BASE64_ENCODED;
    password = CDB_DEFAULT_PASSWORD_BASE64_ENCODED;
    dbDriver = CDB_DEFAULT_JDBC_DRIVER_CLASS_NAME;
  }

  /**
   * Creates a PDB.
   *
   * Verifies that the JDBC url points to a valid CDB$ROOT.
   * Verifies that a PDB with given name does not already exists in database</li>
   * If validations in step 1 and 2 are successful - creates a PDB using performCreatePDB(Connection, String)
   * Verifies that pdb is in MOUNTED state after creation and if that is true
   * opens the pdb and verifies that PDB is in READ WRITE state
   * If PDB has been successfully opened in 5 - verifies the jdbc connection to PDB
   *
   * @param pdbName - Name of the pdb to be created.
   * @return - true if pdb has been successfully created and verified.
   */
  public boolean createPDB(String pdbName) {
    //logger = getLogger();
    boolean status = false;
    if (loadDriver()) {
      logger.info(" Driver is loaded, going to create PDB with name: {0}, JDBC URL: {1}, "
          + "username: {2}, password: {3}", pdbName, jdbcUrl, username, password);
      //try (Connection con = DriverManager.getConnection(jdbcUrl, username, new String(decodePassword(password)));) {
      try (Connection con = DriverManager.getConnection(jdbcUrl, username, password);) {
        logger.info(" Got connection, checking if database with PDB with name: {0}, JDBC URL: {1}, "
            + "username: {2} is a CDB", pdbName, jdbcUrl, username);
        if (isCDB(con)) {
          logger.info("The connected DB is a CDB!");
          if (verifyPDBNotExists(con, pdbName)) {
            logger.info("The PDB name does not exist, is going to  performing create PDB with name: {0} ", pdbName);
            performCreatePDB(con, pdbName);
            logger.info("PDB name {0} is created. We are going to verify its state.", pdbName);
            if (verifyPDBMountedState(con, pdbName)) {
              logger.info("PDB name {0} is created now ", pdbName, "is mounted.");
              performOpenPDB(con, pdbName);
              if (verifyPDBOpenState(con, pdbName)) {
                logger.info(" PDB name {0} is opened", pdbName);
                //String jdbcUrlForPDB = getJdbcUrlForPDB(pdbName);
                //String jdbcUrlForPDB = JDBC_URL_PREFIX + "phx3187e3f.subnet5ad3phx.devweblogicphx.oraclevcn.com:1521/"
                //    + pdbName + PDB_SERVICE_NAME_SUFFIX;
                //"phx3187e3f.subnet5ad3phx.devweblogicphx.oraclevcn.com:1521:pdbName" didn't work!
                String jdbcUrlForPDB = JDBC_URL_PREFIX + PDB_DEFAULT_URL + pdbName + PDB_SERVICE_NAME_SUFFIX;
                logger.info(" PDB name {0} got jdbcUrlForPDB {1}", pdbName, jdbcUrlForPDB);
                status = testJdbcConnection(jdbcUrlForPDB, this.getUsername(), this.getPassword());
                logger.info("PDB name {0} get status {1} for testJdbcConnection at JDBC URL ", pdbName,
                    status, jdbcUrlForPDB);
              } else {
                logger.info(" Creating PDB: PDB name {0} could not be opened.", pdbName);
              }
            } else {
              logger.info(" Creating PDB: PDB name {0} could not be created and mounted at CDB pointed to by ",
                  pdbName, jdbcUrl);
            }
          } else {
            logger.info(" Creating PDB: PDB name {0} already exists in CDB", pdbName);
          }
        } else {
          logger.info("Creating PDB: JDBC url {0} does not point to a valid CDB", jdbcUrl);
        }
      } catch (SQLException e) {
        logger.info(" Creating PDB: Exception occured while creating PDB {0} in in database with JDBC URL {1}, "
            +  "with username {2}", pdbName, jdbcUrl, username);
        logger.info(ExceptionUtils.getStackTrace(e));
      }
    }
    return status;
  }

  /**
   * Drops a PDB.
   *
   * Verifies that the JDBC url points to a valid CDB$ROOT
   * Verifies that a PDB with given name does exists in database and is not already in closed (MOUNTED) state.
   * If validations in step 1 and 2 are successful - closes a PDB using performClosePDB(Connection, String)}
   * Verifies that pdb is in MOUNTED state after closing and if thats true
   * Drops the pdb using performDropPDB(Connection, String)and verifies that PDB no more exists
   *
   * @param pdbName - Name of the pdb to be dropped.
   * @return - true if pdb has been successfully dropped and verified.
   */
  public boolean dropPDB(String pdbName) {
    boolean status = false;
    if (loadDriver()) {
      try (Connection con = DriverManager.getConnection(jdbcUrl, username,
          password);) {
        //    new String(decodePassword(password)));) {
        logger.info("Checking if database with JDBC URL {0},  user {1} is a CDB",
            jdbcUrl,  username);
        if (isCDB(con)) {
          if (verifyPDBExistsAndNotClosed(con, pdbName)) {
            logger.info("Closing PDB ", pdbName);
            performClosePDB(con, pdbName);
            if (verifyPDBClosedState(con, pdbName)) {
              logger.info("PDB {0} closed. ", pdbName);
              performDropPDB(con, pdbName);
              status = verifyPDBNotExists(con, pdbName);
              logger.info("Dropping PDB {0} got status {1}", pdbName, status);
            } else {
              logger.info(" PDB {0} could not be closed at CDB by jdbcUrl {1} ",
                  pdbName, jdbcUrl);
            }
          } else {
            logger.info(" PDB {0} does not exist or is already closed", pdbName);
          }
        } else {
          logger.info(" {0} does not point to a valid CDB.", jdbcUrl);
        }
      } catch (SQLException e) {
        logger.info(" Exception occurred while removing PDB ", pdbName,
            " in database with JDBC URL ", jdbcUrl, " with user ", username);
        logger.info(ExceptionUtils.getStackTrace(e));
      }
    }
    return status;
  }


  /**
   * Encodes the input password to Base64.
   *
   * @param passwordInChars - password to encode.
   * @return - encoded password.
   */
  public static char [] encodePassword(char... passwordInChars) {
    CharBuffer passwordBuffer =  Charset.defaultCharset()
        .decode(Base64.getEncoder().encode(Charset.defaultCharset().encode(CharBuffer.wrap(passwordInChars))));
    char [] encodedPassword = new char[passwordBuffer.length()];
    passwordBuffer.get(encodedPassword);
    return encodedPassword;
  }

  /**
   * Decodes input Base64 encoded password.
   * @param encodedPasswordInChars - input password
   * @return - decoded password.
   */
  public static char [] decodePassword(char... encodedPasswordInChars) {
    CharBuffer passwordBuffer =  Charset.defaultCharset()
        .decode(Base64.getDecoder().decode(Charset.defaultCharset().encode(CharBuffer.wrap(encodedPasswordInChars))));
    char [] decodedPassword = new char[passwordBuffer.length()];
    passwordBuffer.get(decodedPassword);
    return decodedPassword;
  }

  /**
   * Tests a JDBC connection to database specified using input jdbc url, username and password by
   * issuing "select 1 from dual".
   *
   * @param jdbcUrl - jdbc url
   * @param userName - db user name
   * @param base64EncodedPassword - db password base64 encoded
   * @return - true if db ping is successful.
   */
  //public boolean testJdbcConnection(String jdbcUrl, String userName, char[] base64EncodedPassword) {
  public boolean testJdbcConnection(String jdbcUrl, String userName, String base64EncodedPassword) {
    if (loadDriver()) {
      //TODO
      logger.info("Driver is loaded. Is going to test connection with jdbcUrl: {0}, "
          + "username: {1} and passoword: {2}", jdbcUrl, userName, base64EncodedPassword);
      try (Connection con = DriverManager.getConnection(jdbcUrl, userName, base64EncodedPassword);
           //new String(decodePassword(base64EncodedPassword)));
           Statement stmt = con.createStatement();
           ResultSet rs = stmt.executeQuery("select 1 from dual");) {
        logger.info("Testing connection succeed for jdbcUrl" + jdbcUrl);
        return rs.next();
      } catch (SQLException e) {
        logger.info(" Exception occurred while testing JDBC URL ", jdbcUrl, " with user ",
                userName);
        logger.info(ExceptionUtils.getStackTrace(e));
      }
    }
    return false;

  }

  // Getters and Setters for when the defaults are not enough.
  public String getJdbcUrl() {
    return jdbcUrl;
  }

  public void setJdbcUrl(String jdbcUrl) {
    this.jdbcUrl = jdbcUrl;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  //public char[] getPassword() {
  public String getPassword() {
    return password;
  }

  /*public void setPassword(char[] password) {
    this.password = password;
  }*/

  public String getDbDriver() {
    return dbDriver;
  }

  public void setDbDriver(String dbDriver) {
    this.dbDriver = dbDriver;
  }

  /*public static char[] getDefaultPassword() {
    return CDB_DEFAULT_PASSWORD_BASE64_ENCODED;
  }*/

  private boolean isCDB(Connection con) throws SQLException {
    try (PreparedStatement ps = con.prepareStatement(VERIFY_CDB_SQL);
         ResultSet rs = ps.executeQuery()) {
      rs.next();
      return DATABASE_IS_CDB.equals(rs.getString(1));
    }
  }

  private boolean loadDriver() {
    //LoggingFacade logger = getLogger();
    try {
      Class.forName(dbDriver);
    } catch (ClassNotFoundException clex) {
      logger.info("Unable to load ", dbDriver, ", Exception: ", clex.toString());
      logger.info(ExceptionUtils.getStackTrace(clex));
      return false;
    }
    logger.info("Driver is loaded");
    return true;
  }


  private void performCreatePDB(Connection con, String pdbName) throws SQLException {
    executePDBQuery(con, pdbName, CREATE_PDB_SQL);
  }

  private boolean verifyPDBMountedState(Connection con, String pdbName) throws SQLException {
    //TODO
    String pdbStatus = getPDBStatus(con, pdbName);
    logger.info("In verifyPDBMountedState() pdbStatus is: " + pdbStatus);
    return PDB_STATUS_MOUNTED.equals(pdbStatus);
  }

  private void performOpenPDB(Connection con, String pdbName) throws SQLException {
    executePDBQuery(con, pdbName, OPEN_PDB_SQL);
  }

  private boolean verifyPDBOpenState(Connection con, String pdbName) throws SQLException {
    //TODO
    String pdbStatus = getPDBStatus(con, pdbName);
    logger.info("In verifyPDBOpenState() pdbStatus is: " + pdbStatus);
    return PDB_STATUS_READ_WRITE.equals(getPDBStatus(con, pdbName));
  }

  private boolean verifyPDBExistsAndNotClosed(Connection con, String pdbName) throws SQLException {
    return verifyPDBOpenState(con, pdbName);
  }

  private void performClosePDB(Connection con, String pdbName) throws SQLException {
    executePDBQuery(con, pdbName, CLOSE_PDB_SQL);
  }

  private boolean verifyPDBClosedState(Connection con, String pdbName) throws SQLException {
    return verifyPDBMountedState(con, pdbName);
  }

  private void performDropPDB(Connection con, String pdbName) throws SQLException {
    executePDBQuery(con, pdbName, DROP_PDB_SQL);
  }

  private boolean verifyPDBNotExists(Connection con, String pdbName) throws SQLException {
    return PDB_STATUS_NONE.equals(getPDBStatus(con, pdbName));
  }

  private void executePDBQuery(Connection con, String pdbName, String pdbSql) throws SQLException {
    try (Statement stmt = con.createStatement();) {
      stmt.execute(StringUtils.replace(pdbSql, PDB_NAME_TOKEN, pdbName));
    }
  }

  private String getPDBStatus(Connection con, String pdbName) throws SQLException {
    String pdbStatus = PDB_STATUS_NONE;
    try (PreparedStatement ps = con.prepareStatement(PDB_STATUS_SQL);) {
      ps.setString(1, pdbName);
      try (ResultSet rs = ps.executeQuery();) {
        if (rs.next()) {
          pdbStatus = rs.getString(1);
        }
      }
    }
    logger.info("PDB {0} has status {1}", pdbName, pdbStatus);
    return pdbStatus;
  }

  private static String join(Object... msgs) {
    return StringUtils.join(msgs);
  }











}

