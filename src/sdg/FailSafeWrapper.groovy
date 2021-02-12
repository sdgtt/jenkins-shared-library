/**
 * Decorator class to wrap stage closures to ensure graceful failure
 * if stage is a requisite (i.e build status is FAILURE), 
 * use 'true' in second param
 *      ex. def newCls = new FailSafe(oldCls, true)
 * if stage is not a requisite (i.e build status is UNSTABLEs), 
 * use 'false' in second param
 *      ex. def newCls = new FailSafe(oldCls, false)
 */
package sdg
import sdg.NominalException

class FailSafeWrapper {
    private delegate
    private isRequisite
    FailSafeWrapper (delegate,boolean isRequisite=true) {
        this.delegate = delegate
        this.isRequisite = isRequisite
    }
    def invokeMethod(String name, args) {

        if (isRequisite == true){
            return delegate.invokeMethod(name, args)
        }else{
            try{
                return delegate.invokeMethod(name, args)
            }catch(NominalException ex){
                unstable("Stage is unstable. Reason: $ex")  
            }catch (Exception ex){
                error("Stage failed. Error: $ex")
            }
        }
    }
}
