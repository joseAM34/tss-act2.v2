package bo.edu.umss.mercado.statistics;

import java.util.*;

/** Presentation diagnostics from a verified table; never runs or overrides an inferential test. */
public final class ChiSquareDetails {
    private ChiSquareDetails(){}
    public static final String H0="H₀: las dos variables categóricas son independientes en la población de referencia.";
    public static final String H1="H₁: existe asociación entre las dos variables categóricas en la población de referencia.";
    public static final String EXPECTED="Eᵢⱼ = (total de fila i × total de columna j) / n; n = pares con valor incluidos.";
    public static final String CONTRIBUTION="Aporteᵢⱼ = (Oᵢⱼ − Eᵢⱼ)² / Eᵢⱼ; χ² = suma de aportes sobre las filas y columnas activas.";
    public static final String RESIDUAL="Residuo de Pearsonᵢⱼ = (Oᵢⱼ − Eᵢⱼ) / √Eᵢⱼ. Su cuadrado coincide con el aporte de la celda.";
    public static final String NOTE="Estos residuos son de Pearson, no residuos ajustados ni pruebas por celda. Un signo positivo indica más observaciones que las esperadas y uno negativo indica menos. No prueban causalidad ni sustituyen las condiciones de aplicabilidad. Si E es cero o no hay base, el cociente no se calcula.";
    @SuppressWarnings("unchecked")
    public static Map<String,Object> describe(Map<String,Object> table){
        var observed=(List<List<Number>>)table.get("observed");var expected=(List<List<Number>>)table.get("expected");
        var cells=new ArrayList<Map<String,Object>>();double sum=0;int usable=0;
        for(int r=0;r<observed.size();r++)for(int c=0;c<observed.get(r).size();c++){
            Number o=observed.get(r).get(c),e=expected.get(r).get(c);Double difference=e==null?null:o.doubleValue()-e.doubleValue();
            Double contribution=null,residual=null;
            if(e!=null&&Double.isFinite(e.doubleValue())&&e.doubleValue()>0){residual=difference/Math.sqrt(e.doubleValue());contribution=residual*residual;if(!Double.isFinite(contribution))throw new IllegalStateException("Detalle de chi-cuadrado no finito.");sum+=contribution;usable++;}
            var cell=new LinkedHashMap<String,Object>();cell.put("row",r);cell.put("column",c);cell.put("observed",o);cell.put("expected",e);cell.put("difference",difference);cell.put("contribution",contribution);cell.put("pearson_residual",residual);cells.add(cell);
        }
        boolean executed=table.get("p")!=null;
        String decision=!executed?"Prueba no ejecutada: no se emite una decisión sobre H₀. La suma diagnóstica no autoriza una conclusión de asociación.":Boolean.TRUE.equals(table.get("reject_h0"))?"Se rechaza H₀ al nivel α registrado: hay evidencia estadística de asociación. No implica causalidad.":"No se rechaza H₀ al nivel α registrado: no hay evidencia suficiente de asociación. Esto no demuestra independencia.";
        var result=new LinkedHashMap<String,Object>();result.put("protocol","DETALLE_CHI_CUADRADO_V1");result.put("h0",H0);result.put("h1",H1);result.put("expected_formula",EXPECTED);result.put("contribution_formula",CONTRIBUTION);result.put("residual_formula",RESIDUAL);result.put("notes",NOTE);result.put("decision",decision);result.put("cells",cells);result.put("diagnostic_sum",usable==0?null:sum);result.put("calculated_cells",usable);result.put("uncalculated_cells",cells.size()-usable);result.put("test_executed",executed);return result;
    }
}
