package bo.edu.umss.mercado.statistics;

/** Only the current global dictionary can be recovered with administrator confirmation. */
public class DictionaryIntegrityException extends RuntimeException {
    public DictionaryIntegrityException(){
        super("El diccionario no coincide con su huella. Abre /admin/diccionario/recuperar para revisar y aceptar su contenido actual.");
    }
}
