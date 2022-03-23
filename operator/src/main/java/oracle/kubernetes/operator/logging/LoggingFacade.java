// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.logging.Formatter;
import java.util.logging.Logger;

/** Centralized logging for the operator. */
public class LoggingFacade extends BaseLoggingFacade {

  /**
   * Construct logging facade.
   *
   * @param logger logger
   */
  public LoggingFacade(Logger logger) {
    super(logger);
  }

  @Override
  Formatter getLoggingFormatter() {
    return new OperatorLoggingFormatter();
  }
}
