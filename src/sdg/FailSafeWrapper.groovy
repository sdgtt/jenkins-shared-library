/**
 * Decorator class to wrap stage closures to ensure graceful failure
 * if stage is a requisite (i.e build status is FAILURE), 
 *  use 'true' in second param
 *      ex. def newCls = new FailSafe(oldCls, true)
 *  before exiting, nextDelegate wil be executed if supplied
 *      ex. def newCls = new FailSafe(oldCls, true, backupCls)
 * if stage is not a requisite (i.e build status is UNSTABLE), 
 *  use 'false' in second param
 *      ex. def newCls = new FailSafe(oldCls, false)
 */
package sdg
import sdg.NominalException

class FailSafeWrapper {
    private delegate
    private isRequisite
    private nextDelegate
    FailSafeWrapper (delegate,boolean isRequisite=true, nextDelegate=null) {
        this.delegate = delegate
        this.isRequisite = isRequisite
        this.nextDelegate = nextDelegate
    }
    def invokeMethod(String name, args) {

        if (isRequisite == true){
            try{
                delegate.invokeMethod(name, args)
            }catch (Exception ex){
                echo "Stage failed. Error ${ex.getMessage()}"
                if (nextDelegate) {
                    try {
                        nextDelegate.invokeMethod(name, args)
                    }catch (Exception ex_d){
                        echo "Delegated stage failed. Error ${ex_d.getMessage()}"
                    }
                }
                throw ex
            }
        }else{
            try{
                delegate.invokeMethod(name, args)
            }catch(NominalException ex){
                unstable("Stage is unstable. Reason: ${ex.getMessage()}")  
            }catch (Exception ex){
                echo "Stage failed. Error ${ex.getMessage()}"
                throw ex
            }
        }
    }
}
