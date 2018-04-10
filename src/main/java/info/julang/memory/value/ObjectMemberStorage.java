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

package info.julang.memory.value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import info.julang.execution.symboltable.TypeTable;
import info.julang.external.exceptions.JSEError;
import info.julang.memory.MemoryArea;
import info.julang.typesystem.JType;
import info.julang.typesystem.jclass.ClassMemberLoaded;
import info.julang.typesystem.jclass.ClassMemberMap;
import info.julang.typesystem.jclass.ICompoundType;
import info.julang.typesystem.jclass.JClassFieldMember;
import info.julang.typesystem.jclass.JClassMember;
import info.julang.typesystem.jclass.JClassType;
import info.julang.typesystem.jclass.builtin.JConstructorType;
import info.julang.typesystem.jclass.builtin.JMethodType;
import info.julang.util.OneOrMoreList;

/**
 * A class for hierarchically storing member values of an object.
 * 
 * @author Ming Zhou
 */
public class ObjectMemberStorage {

	// We use a map with key = member's name and value = OneOrMoreList<ObjectMember> to store all the members.
	// If a member is overridden in a subclass, it will be stored here by having a value that contains more
	// than one ObjectMember, in the bottom-up order of definition. Note ObjectMember also contains a rank 
	// field which can tell which class contributes to the definition of that member.
	private Map<String, OneOrMoreList<ObjectMember>> members;
	
	private ClassMemberMap cmm;
	
	public static ObjectMemberStorage makeEmptyObjectMemberStorage(){
		return new ObjectMemberStorage();
	}
	
	private ObjectMemberStorage(){
		members = new HashMap<String, OneOrMoreList<ObjectMember>>();
	}
	
	ObjectMemberStorage(
		MemoryArea memory, 
		JClassType classTyp, 
		ObjectValue thisValue,
		boolean addMethod,
		boolean sealConst){
		this.cmm = classTyp.getMembers(thisValue == null); // get static members if this value is null
		Map<String, OneOrMoreList<ClassMemberLoaded>>[] mems = cmm.getDefinedMembers();
		int len = mems.length;
		if(members == null){
			members = new HashMap<String, OneOrMoreList<ObjectMember>>();
		}
		
		for(int i = 0; i < len; i++){
			Map<String, OneOrMoreList<ClassMemberLoaded>> memMap = mems[i];
			// Initialize only if there is any member defined at this class.
			if(memMap != null && memMap.size() > 0){
				JValue val = null;
				Set<Entry<String, OneOrMoreList<ClassMemberLoaded>>> set = memMap.entrySet();
				for(Entry<String, OneOrMoreList<ClassMemberLoaded>> e : set){
					for(ClassMemberLoaded cml : e.getValue()){
						JClassMember member = cml.getClassMember();
						switch(member.getMemberType()){
						case FIELD:
							val = ValueUtilities.makeDefaultValue(memory, member.getType(), sealConst && ((JClassFieldMember)member).isConst(), TypeTable.getInstance());	
							break;
						case METHOD:
							if(addMethod){
								val = makeFuncValue(memory, member.getType(), thisValue, true); // The last argument is for initializing function members			
							}
							break;
						default:
							break;
						}
						
						if(val != null){
							addMemberValue(e.getKey(), i, val);
						}				
					}
				}
			}
		}
	}
	
	/**
	 * A special method for adding method members to value of Function class.
	 * <p/>
	 * This exists because we must break an self-referencing loop during initialization 
	 * of the very special Function class, which itself contains Function members.
	 * 
	 * @param memory
	 * @param classTyp
	 * @param thisValue
	 */
	void populateMethodMembersForFunctionType(
		MemoryArea memory, 
		JClassType classTyp, 
		ObjectValue thisValue){
		ClassMemberMap cmm = classTyp.getMembers(false);
		Map<String, OneOrMoreList<ClassMemberLoaded>>[] mems = cmm.getDefinedMembers();
		int len = mems.length;
		
		List<ValueWithRank<RefValue>> list = new ArrayList<ValueWithRank<RefValue>>();
		for(int i = 0; i < len; i++){
			Map<String, OneOrMoreList<ClassMemberLoaded>> memMap = mems[i];
			// Initialize only if there is any member defined at this class.
			if(memMap != null && memMap.size() > 0){
				Set<Entry<String, OneOrMoreList<ClassMemberLoaded>>> set = memMap.entrySet();
				for(Entry<String, OneOrMoreList<ClassMemberLoaded>> e : set){
					for(ClassMemberLoaded cml : e.getValue()){
						JClassMember member = cml.getClassMember();
						switch(member.getMemberType()){
						case METHOD:
							// Create func value for each member.
							//
							// It is very important here to not add function members to each func value, otherwise
							// it would become an infinite loop. For example, since a func value V contains a func 
							// member of name "toString()", we need to create another func value V2 for "toString()" 
							// to add to V's instance members. And for V2's "toString()" method we need to create
							// V3, and so on. 
							//
							// In fact, V1, V2 and V3 make no difference between each other and should share the
							// same value. To make that possible, we break the loop right after V1 is created, then
							// we add V1 to itself as the instance member for "toString()". The same applies to 
							// other function members of Function type.
							
							// (The last argument is for NOT initializing function members)
							RefValue rv = makeFuncValue(memory, member.getType(), thisValue, false);
							
							// We would add them into member storage here if we were about normal initialization.
							// But to break out the loop let's defer that for a while. Memoize these members in a 
							// list for now.
							list.add(new ValueWithRank<RefValue>(e.getKey(), i, rv));
							break;
						default:
							break;
						}
					}
				}
			}
		}
		
		// Let's say for a function value, F0, it contains 3 function members: F1, F2 and 
		// F3, then what this loop does is to have each member also contain the three members
		// on its own.
		
		// For each function value
		for(ValueWithRank<RefValue> vwr : list){
			ObjectMemberStorage storage = vwr.getVal().getReferredValue().members;		
			
			// Add methods to its member storage, which now contains only the fields.	
			for(int j = 0; j < list.size(); j++){
				ValueWithRank<RefValue> vwr2 = list.get(j);
				storage.addMemberValue(vwr2.getName(), vwr2.getRank(), vwr2.getVal());
			}
		}
		
		// (By now we have F1, F2 and F3 initialized)
		
		// Add function members to this value at the very end
		for(ValueWithRank<RefValue> vwr : list){
			addMemberValue(vwr.getName(), vwr.getRank(), vwr.getVal());
		}
		
		// (By now we have F0 initialized)
	}
	
	/**
	 * Get the member by name. If the member of same name is defined more than once in the hierarchy, returns the
	 * one at bottom (overriding the member from ancestors).
	 * 
	 * @param name
	 * @param startingType start searching from this type upwards; 
	 * if null, from the bottom (the type this value is of by declaration).
	 * @return a list of overloaded members for that name.
	 */
	OneOrMoreList<ObjectMember> getMemberByName(String name, ICompoundType startingType){
		OneOrMoreList<ObjectMember> memberList = members.get(name);
		if(memberList != null){
			if(startingType == null){
				return memberList;
			} else {
				OneOrMoreList<ClassMemberLoaded> cmls = cmm.getLoadedMemberByName(startingType, name);
				if(cmls != null){
					List<ObjectMember> tlist = new ArrayList<>();
					for(ClassMemberLoaded cml :cmls){
						int rank = cml.getRank();
						for(ObjectMember om : memberList){
							if(om.getClassRank() >= rank){
								tlist.add(om);
							}
						}		
					}
					return new OneOrMoreList<ObjectMember>(tlist);
				}
			}
		}
		
		return null;
	}
	
	private void addMemberValue(String name, int rank, JValue val){
		ObjectMember om = new ObjectMember(val, rank);
		OneOrMoreList<ObjectMember> memberList = members.get(name);
		if(memberList == null){
			memberList = new OneOrMoreList<ObjectMember>(om);
			members.put(name, memberList);
		} else {
			memberList.add(om);
		}
	}
	
	private RefValue makeFuncValue(MemoryArea memory, JType type, ObjectValue thisValue, boolean initFuncMembers) {
		// If thisValue == null, this is a static member being added to type value
		// If thisValue != null, this is an instance member being added to object value, 
		// and we must create a new one for each instance as it contains a reference to that instance 
		RefValue refVal = null;
		if(type instanceof JMethodType){
			refVal = new RefValue(
				memory, 
				thisValue == null ?
				MethodValue.createStaticMethodValue(memory, (JMethodType) type, initFuncMembers) :		
				MethodValue.createInstanceMethodValue(memory, (JMethodType) type, thisValue, initFuncMembers));
		} else if(type instanceof JConstructorType){
			refVal = new RefValue(
				memory, 
				new CtorValue(memory, (JConstructorType) type, thisValue, initFuncMembers));
		}
		if(refVal != null){
			refVal.setConst(true);
			return refVal;
		}
		
		throw new JSEError("Cannot initialize an object whose type is unknown.", this.getClass());
	}
	
	class ValueWithRank<T extends JValue> {
		private int rank;
		private T val;
		private String name;
		public ValueWithRank(String name, int rank, T val) {
			this.name = name;
			this.rank = rank;
			this.val = val;
		}
		String getName(){
			return name;
		}
		int getRank() {
			return rank;
		}
		T getVal() {
			return val;
		}
	}
}
