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
        if (name == 'maximumNumberOfParameters')
            return delegate.maximumNumberOfParameters
        try{
            delegate.invokeMethod(name, args)
        }catch(NominalException ex){
            unstable("Stage is unstable. Reason: ${ex.getMessage()}")
            if (isRequisite == true){
                throw ex
            }
        }catch (Exception ex){
            if (nextDelegate) {
                try {
                    nextDelegate.invokeMethod(name, args)
                }catch (Exception ex_d){
                    echo "Delegated stage failed. Error ${ex_d.getMessage()}"
                }
            }
            throw ex
        }
    }
    private def String getStackTrace(Throwable aThrowable){
        // Utility method to print the stack trace of an error
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true);
        aThrowable.printStackTrace(ps);
        return baos.toString();
    }

}
