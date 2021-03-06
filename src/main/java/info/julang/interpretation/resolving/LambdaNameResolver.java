/*
MIT License

Copyright (c) 2017 Ming Zhou

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package info.julang.interpretation.resolving;

import info.julang.execution.symboltable.Display;
import info.julang.execution.symboltable.ITypeTable;
import info.julang.execution.symboltable.IVariableTable;
import info.julang.interpretation.context.ContextType;
import info.julang.memory.value.JValue;
import info.julang.memory.value.ObjectMember;
import info.julang.memory.value.ObjectValue;
import info.julang.memory.value.TempValueFactory;
import info.julang.memory.value.TypeValue;
import info.julang.typesystem.jclass.Accessibility;
import info.julang.typesystem.jclass.ICompoundType;
import info.julang.util.OneOrMoreList;

/**
 * {@link INameResolver Name resolver} used for lambda.
 * 
 * @author Ming Zhou
 */
public class LambdaNameResolver implements INameResolver {

	private IVariableTable vt;
	
	private ITypeTable tt;
	
	private Display display;
	
	private ObjectValue thisValue;
	
	private TypeValue tvalue;
	
	private ICompoundType containingType;
	
	private ContextType definingContextType;
	
	public LambdaNameResolver(
		IVariableTable vt, ITypeTable tt, Display display, ContextType definingContextType, ICompoundType containingType){
		this.vt = vt;
		this.tt = tt;
		this.display = display;
		this.definingContextType = definingContextType;
		this.containingType = containingType;
	}
	
	@Override
	public JValue resolve(String id){
		if(definingContextType == ContextType.IMETHOD){
			// 1) "this"?
			if("this".equals(id)){//KnownTokens.THIS_T.getLiteral())
				initThisValue();
				return thisValue;
			}
		}
		
		// 2) try as variable; query global variable table only if the lambda is defined in global context.
		JValue v = vt.getVariable(id, definingContextType == ContextType.FUNCTION);
		if(v != null){
			return v;
		}
		
		// 3) try from display
		v = display.getVariable(id);
		if(v != null){
			return v;
		}
		
		// 4) class member
		v = getMember(id);
		if(v != null){
			return v;
		}
		
		// 5) try as type
		v = tt.getValue(id);
		if(v != null){
			return v;
		}
		
		// throw new UndefinedVariableNameException(id);
		
		return null;
	}
	
	private void initThisValue() {
		if(thisValue == null) {
			thisValue = (ObjectValue) display.getVariable("this");
		}
	}

	private JValue getMember(String name){
		if(definingContextType == ContextType.IMETHOD){
			initThisValue();
			
			ICompoundType jct = Accessibility.checkMemberAccess(thisValue.getClassType(), name, containingType, null, ContextType.IMETHOD, false, false);
			
			OneOrMoreList<ObjectMember> mems = thisValue.getMemberValueByClass(name, jct, true);
			if(mems == null){
				return null;
			} else if(mems.hasOnlyOne()){
				return mems.getFirst().getValue();
			} else {
				return TempValueFactory.createTempMethodGroupValue(mems);
			}
		} else if(definingContextType == ContextType.SMETHOD) {
			if(tvalue == null){
				tvalue = tt.getValue(containingType.getName());
			}
			
			return tvalue.getMemberValue(name);
		} else {
			return null;
		}
	}
}
