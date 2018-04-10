package info.jultest.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import info.julang.external.EngineFactory;
import info.julang.external.EngineFactory.EngineParamPair;
import info.julang.external.exceptions.EngineInvocationError;
import info.julang.external.interfaces.IExtEngineRuntime;
import info.julang.external.interfaces.IExtValue;
import info.julang.external.interfaces.IExtVariableTable;
import info.julang.external.interfaces.JValueKind;
import info.julang.external.interfaces.IExtValue.IStringVal;

/**
 * A helper classes used for {@link EngineFactory}-based tests. Engines and components 
 * created from this class rooted in a different class loader.
 * <p/>
 * By design, Julian Script Engine must be created from {@link EngineFactory} in order
 * to isolate itself from other engine instances. To reduce the overhead of system 
 * initialization and resource allocation, most unit tests do not create engine this way. 
 * In most cases, sharing the same JVM environment will not cause an issue, but it may 
 * prove problematic for certain test cases which are sensitive to the macro environment
 * or timings. Therefore, when a test appears shaky, consider using the utilities provided 
 * by this class.
 * 
 * @author Ming Zhou
 */
public final class EFCommons {

	public final static String EXTERNAL_ROOT = Commons.SRC_REPO_ROOT + "ExternalAPI/";
	
	//-------------------------------------- Run script engine --------------------------------------//
	
	/**
	 * Run a script under [SRC]/julian/ExternalAPI/
	 * 
	 * @param scriptName
	 * @throws EngineInvocationError  
	 */
	public static IExtEngineRuntime runViaFactory(String scriptName) throws EngineInvocationError {
		String path = EXTERNAL_ROOT + scriptName;
		EngineFactory fact = new TestCaseEngineFactory();
		EngineParamPair pair = fact.createEngineAndRuntime();
		pair.getFirst().runFile(path);
		return pair.getSecond();
	}
	
	/**
	 * Run a script under [SRC]/julian/Interpret/{group}/{feature}/
	 * @param group
	 * @param feature can be null. The script file will be located directly under [SRC]/julian/Interpret/{group}.
	 * @param scriptName
	 */
	public static IExtEngineRuntime runViaFactory(String group, String feature, String scriptName) throws EngineInvocationError {
		String path = Commons.makeScriptPath(group, feature, scriptName);
		EngineFactory fact = new TestCaseEngineFactory();
		EngineParamPair pair = fact.createEngineAndRuntime();
		pair.getFirst().runFile(path);
		return pair.getSecond();
	}
	
	//-------------------------------------- Retrieval methods ---------------------------------------//
	
	public static IExtValue.IIntVal getIntValue(IExtVariableTable vt, String varName){
		IExtValue value = vt.getValue(varName);
		assertNotNull(value);
		assertEquals(JValueKind.INTEGER, value.getKind());
		IExtValue.IIntVal ivalue = (IExtValue.IIntVal) value;
		return ivalue;
	}
	
	//-------------------------------------- Validation methods --------------------------------------//
	
	/**
	 * Validate that the variable is of int type and contains the specified value.
	 */
	public static void validateIntValue(IExtVariableTable vt, String varName, int v){
		IExtValue.IIntVal ivalue = getIntValue(vt, varName);
		assertEquals(v, ivalue.getIntValue());
	}
	
	/**
	 * Validate that the variable is of bool type and contains the specified value.
	 */
	public static void validateBoolValue(IExtVariableTable vt, String varName, boolean v){
		IExtValue value = vt.getValue(varName);
		assertNotNull(value);
		assertEquals(JValueKind.BOOLEAN, value.getKind());
		IExtValue.IBoolVal ivalue = (IExtValue.IBoolVal) value;
		assertEquals(v, ivalue.getBoolValue());
	}
	
	/**
	 * Validate that the variable is of byte type and contains the specified value.
	 * <p/>
	 * To spare explicit casting, this method takes an int argument.
	 */
	public static void validateByteValue(IExtVariableTable vt, String varName, int v){
		IExtValue value = vt.getValue(varName);
		assertNotNull(value);
		assertEquals(JValueKind.BYTE, value.getKind());
		IExtValue.IByteVal ivalue = (IExtValue.IByteVal) value;
		assertEquals(v, ivalue.getByteValue());
	}
	
	/**
	 * Validate that the variable is of char type and contains the specified value.
	 */
	public static void validateCharValue(IExtVariableTable vt, String varName, char v){
		IExtValue value = vt.getValue(varName);
		assertNotNull(value);
		assertEquals(JValueKind.CHAR, value.getKind());
		IExtValue.ICharVal ivalue = (IExtValue.ICharVal) value;
		assertEquals(v, ivalue.getCharValue());
	}
	
	/**
	 * Validate that the variable is of float type and contains the specified value.
	 */
	public static void validateIntValue(IExtVariableTable vt, String varName, float v){
		IExtValue value = vt.getValue(varName);
		assertNotNull(value);
		assertEquals(JValueKind.FLOAT, value.getKind());
		IExtValue.IFloatVal ivalue = (IExtValue.IFloatVal) value;
		assertEquals(v, ivalue.getFloatValue(), 0.001f);
	}
	
	/**
	 * Validate that the variable is of string type and contains the specified value.
	 */
	public static void validateStringValue(IExtVariableTable vt, String varName, String v){
		IExtValue value = vt.getValue(varName);
		assertNotNull(value);
		IStringVal sv = (IStringVal)value;
		assertEquals(v, sv.getStringValue());
	}
}