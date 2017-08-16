package hgm.asve.factor;

import hgm.Variable;

import java.util.Set;

/**
 * Created by Hadi Afshar.
 * Date: 19/09/13
 * Time: 2:34 PM
 */

/*
 An interface of all factors
 */
@Deprecated
public interface OLD_IFactor {
    //todo: associated variable is a characteristic fo model not factor.

    /**
     * @return The variable associated with the factor. Null if none.
     */
    public Variable getAssociatedVar();

    /**
     * @return All scope variables (INCLUDING the associated variable)
     */
    public Set<Variable> getScopeVars();
}
