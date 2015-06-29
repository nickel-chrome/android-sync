package org.mozilla.gecko.background.common.log;

import org.apache.commons.logging.LogFactory;

public class Logger {

	private static boolean initLog = false;
	
	public static void init(String level) {
		initLog = true;
	
		//Initialise Apache commons logging
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showlogname", "true");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.defaultlog", level);
	}

	public static org.apache.commons.logging.Log getInstance(String context) {
		if ( !initLog ) {
			System.err.println("Log not initialised, setting default log level to warn");
			init("warn");
		}	
		return LogFactory.getLog(context);
	}
	
	public static void setLogLevel(String logger, String level) {
		if ( !initLog ) {
			System.err.println("Log not initialised, setting default log level to warn");
			init("warn");
		}
		System.setProperty("org.apache.commons.logging.simplelog.log." + logger, level);
	}
	
	public static void trace(String tag, String message) { getInstance(tag).trace(message); }
	public static void debug(String tag, String message) { getInstance(tag).debug(message); }
	public static void info(String tag, String message)  { getInstance(tag).info(message); }
	public static void warn(String tag, String message)  { getInstance(tag).warn(message); }
	public static void error(String tag, String message) { getInstance(tag).error(message); }
	public static void fatal(String tag, String message) { getInstance(tag).fatal(message); }

	public static void t(String tag, String message)     { Logger.trace(tag, message); }
	public static void d(String tag, String message)     { Logger.debug(tag, message); }
	public static void i(String tag, String message)     { Logger.info (tag, message); }
	public static void w(String tag, String message)     { Logger.warn (tag, message); }
	public static void e(String tag, String message)     { Logger.error(tag, message); }
	public static void wtf(String tag, String message)   { Logger.fatal(tag, message); }	

	public static void trace(String tag, Throwable e) { Logger.trace(tag, e.getLocalizedMessage()); }
	public static void debug(String tag, Throwable e) { Logger.debug(tag, e.getLocalizedMessage()); }
	public static void info(String tag, Throwable e)  { Logger.info (tag, e.getLocalizedMessage()); }
	public static void warn(String tag, Throwable e)  { Logger.warn (tag, e.getLocalizedMessage()); }
	public static void error(String tag, Throwable e) { Logger.error(tag, e.getLocalizedMessage()); }
	public static void fatal(String tag, Throwable e) { Logger.fatal(tag, e.getLocalizedMessage()); }

	public static void trace(String tag, String message, Throwable e) { Logger.trace(tag, message + " - " + e.getLocalizedMessage()); }
	public static void debug(String tag, String message, Throwable e) { Logger.debug(tag, message + " - " + e.getLocalizedMessage()); }
	public static void info(String tag, String message, Throwable e)  { Logger.info (tag, message + " - " + e.getLocalizedMessage()); }
	public static void warn(String tag, String message, Throwable e)  { Logger.warn (tag, message + " - " + e.getLocalizedMessage()); }
	public static void error(String tag, String message, Throwable e) { Logger.error(tag, message + " - " + e.getLocalizedMessage()); }
	public static void fatal(String tag, String message, Throwable e) { Logger.fatal(tag, message + " - " + e.getLocalizedMessage()); }

}
