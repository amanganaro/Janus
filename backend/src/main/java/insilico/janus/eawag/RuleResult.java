package insilico.janus.eawag;

/**
 *
 * @author Alberto
 */
public class RuleResult {

    public String AppliedRule;
    public String AppliedRuleDescription;
    public int AppliedRuleLikelihood;
    public String Result;
    public String Remarks;
    
    public RuleResult() {
        AppliedRule = "";
        AppliedRuleDescription= "";
        AppliedRuleLikelihood = EawagRule.LH_NOT_SET;
        Result = "";
    }
    
}
