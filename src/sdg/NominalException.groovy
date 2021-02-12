/**
 * Custom exception for non-fatal errors
 */
package sdg

class NominalException extends Exception { 
    NominalException(String errorMessage) {
        super(errorMessage);
    }
}