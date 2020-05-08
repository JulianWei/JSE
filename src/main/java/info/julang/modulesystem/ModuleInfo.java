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

package info.julang.modulesystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import info.julang.execution.Argument;
import info.julang.execution.threading.ThreadRuntime;
import info.julang.interpretation.context.Context;
import info.julang.interpretation.internal.NewObjExecutor;
import info.julang.interpretation.syntax.ParsedTypeName;
import info.julang.memory.value.HostedValue;
import info.julang.memory.value.ObjectValue;
import info.julang.memory.value.TypeValue;
import info.julang.modulesystem.prescanning.IRawScriptInfo;
import info.julang.modulesystem.prescanning.RawClassInfo;
import info.julang.parser.LazyAstInfo;
import info.julang.typesystem.jclass.JClassConstructorMember;
import info.julang.typesystem.jclass.JClassType;
import info.julang.typesystem.jclass.jufc.System.Reflection.ScriptModule;

/**
 * This class contains all the known info about a module, including its name, the script files belonging to
 * this module, it requirements, and all the classed defined in the module.
 * 
 * @author Ming Zhou
 */
public class ModuleInfo {

	public static final String DEFAULT_MODULE_NAME = "<default>";
	
	private String name;
	
	protected List<ScriptInfo> scripts;
	
	protected List<ClassInfo> classes;
	
	protected List<ModuleInfo> requirements;
	
	public ModuleInfo(String name){
		this.name = name;
	}
	
	public String getName(){
		return name;
	}

	public ScriptInfo getFirstScript() {
		return scripts.get(0);
	}
	
	public List<ScriptInfo> getScripts() {
		return scripts;
	}

	public List<ClassInfo> getClasses() {
		return classes;
	}

	public List<ModuleInfo> getRequirements() {
		return requirements;
	}
	
	/**
	 * Merge from the given module info. The current one should take precedence.
	 * 
	 * @param src the module info to be merged from, but will not overwrite the
	 * current one whenever there is a conflict.
	 */
	public void mergeFrom(ModuleInfo src) {
		// (No need to merge scripts info )
		// this.scripts.addAll(src.scripts);
		
		this.classes = mergeLists(src.classes, this.classes);
		this.requirements = mergeLists(src.requirements, this.requirements);
		
		// NOTE: we don't set new requirement list to each ScriptInfo instance. 
		// This means classed defined in a previous line won't get bind to new
		// namespace, if any is introduced later. As of 0.2.0, this is by design.
		//
		// However, we must reset requirement info for the very 1st ScriptInfo,
		// which is used to populate namespace ahead of global script execution.
		ScriptInfo currScript = this.getFirstScript();
		currScript.setRequirements(mergeLists(src.getFirstScript().getRequirements(), currScript.getRequirements()));
	}
	
	/**
	 * Merge two lists. <code>src</code> list would overwrite any element in the <code>dst</code> list if
	 * it equals() to the element.
	 */
	private <T> List<T> mergeLists(List<T> dst, List<T> src) {
		Set<T> set = new HashSet<T>();
		set.addAll(src);
		set.addAll(dst); // When adding an element from dst, if the element already exists in set, no-op. 
		List<T> list = new ArrayList<T>();
		list.addAll(set);
		return list;
	}

	public static class MutableModuleInfo extends ModuleInfo {

		public MutableModuleInfo(String name) {
			super(name);
			requirements = new ArrayList<ModuleInfo>();
		}
		
		private List<String> requiredModuleNames;
		
		List<String> getRequiredModuleNames() {
			return requiredModuleNames;
		}
		
		void addRequiredModule(ModuleInfo info){
			requirements.add(info);
		}
	}
	
	public static class Builder {
		
		private MutableModuleInfo info;
		
		public Builder(String name){
			info = new MutableModuleInfo(name);
		}
		
		public MutableModuleInfo build(){
			return info;
		}
		
		public void addScript(IRawScriptInfo script){
			LazyAstInfo ainfo = script.getAstInfo();
			
			// Script file info
			if(info.scripts == null){
				info.scripts = new ArrayList<ScriptInfo>();
			}
			
			ScriptInfo scriptFile = new ScriptInfo(
				info,
				script.getScriptFilePath(), 
				script.getRequirements(),
				ainfo);
			
			info.scripts.add(scriptFile);
			
			// Requirements
			if(info.requiredModuleNames == null){
				info.requiredModuleNames = new ArrayList<String>();
			}
			for(RequirementInfo reqMod : script.getRequirements()){ // guaranteed to be not duplicated
				info.requiredModuleNames.add(reqMod.getName());
			}
			
			// Classes
			if(info.classes == null){
				info.classes = new ArrayList<ClassInfo>();
			}
			for(RawClassInfo rci : script.getClasses()){ // guaranteed to be not duplicated
				info.classes.add(
					new ClassInfo(
						info.getName() + "." + rci.getName(), 
						rci.getName(), 
						rci.getDeclInfo(),
						scriptFile));
			}
		}
	}
	
	//---------------------------------- Object ----------------------------------//

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ModuleInfo other = (ModuleInfo) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
	
	@Override
	public String toString(){
		return name;
	}
    
    private ObjectValue modObject;
    
    /**
     * Get <font color="green"><code>System.Reflection.Module</code></font> object for this module. 
     * This object will be created the first time this method is called.
     * 
     * @param runtime
     */
    public ObjectValue getOrCreateScriptObject(ThreadRuntime runtime) {
        if (modObject == null) {
            synchronized(TypeValue.class){
                if (modObject == null) {
                    JClassType modClassType = (JClassType) runtime.getTypeTable().getType(ScriptModule.FQCLASSNAME);
                    if (modClassType == null) {
                        Context context = Context.createSystemLoadingContext(runtime);
                        runtime.getTypeResolver().resolveType(context, ParsedTypeName.makeFromFullName(ScriptModule.FQCLASSNAME), true);
                        modClassType = (JClassType) runtime.getTypeTable().getType(ScriptModule.FQCLASSNAME);
                    }
                    
                    JClassConstructorMember modClassCtor = modClassType.getClassConstructors()[0];
                    
                    NewObjExecutor noe = new NewObjExecutor(runtime);
                    ObjectValue ov = noe.newObjectInternal(modClassType, modClassCtor, new Argument[0]);
                    
                    ScriptModule st = new ScriptModule();
                    st.setModule(this);
                    HostedValue hv = (HostedValue)ov;
                    hv.setHostedObject(st);
                    
                    modObject = ov;
                }
            }
        }
        
        return modObject;
    }
}
