package burlap.domain.singleagent.tabularized;

import java.util.ArrayList;
import java.util.List;

import org.rlcommunity.rlglue.codec.taskspec.ranges.DoubleRange;

import burlap.behavior.singleagent.auxiliary.StateEnumerator;
import burlap.behavior.statehashing.DiscreteMaskHashingFactory;
import burlap.behavior.statehashing.StateHashFactory;
import burlap.domain.singleagent.gridworld.GridWorldDomain;
import burlap.oomdp.auxiliary.DomainGenerator;
import burlap.oomdp.auxiliary.StateGenerator;
import burlap.oomdp.core.Attribute;
import burlap.oomdp.core.Domain;
import burlap.oomdp.core.ObjectClass;
import burlap.oomdp.core.ObjectInstance;
import burlap.oomdp.core.State;
import burlap.oomdp.core.TerminalFunction;
import burlap.oomdp.core.TransitionProbability;
import burlap.oomdp.core.Attribute.AttributeType;
import burlap.oomdp.singleagent.Action;
import burlap.oomdp.singleagent.GroundedAction;
import burlap.oomdp.singleagent.RewardFunction;
import burlap.oomdp.singleagent.SADomain;
import burlap.oomdp.singleagent.interfaces.rlglue.RLGlueEnvironment;


/**
 * In general, it is suggested algorithms be designed to work with either factored state representations or the BURLAP State Hashing. However,
 * some algorithms may be limited to working with states that have explicit enumerated values and with an entire state space already defined. In particular,
 * if you are interfacing with code external to BURLAP this may be required. This domain generator can take any input domain that does not have parameterized
 * actions and turn it into a domain in which states are represented by a single int attribute and are fully enumerated. 
 * The state space used must be enumerated before calling the {@link generateDomain()} method and is performed
 * using a BURLAP state enumerator. In particular, seed states must be passed to this object and it will find all reachable states from the seed state
 * and enumerate them.
 * @author James MacGlashan
 *
 */
public class TabulatedDomainWrapper implements DomainGenerator {

	/**
	 * The single attribute name for identifying states
	 */
	public static final String						ATTSTATE = "state";
	
	/**
	 * The single class name that holds the state attribute
	 */
	public static final String						CLASSSTATE = "state";
	
	
	/**
	 * The input domain that is to be wrapped into a tabularized domain
	 */
	protected Domain								inputDomain;
	
	/**
	 * The output tabularied domain
	 */
	protected Domain								tabDomain;
	
	/**
	 * The state enumerator used for enumerating (or tabulating) all states
	 */
	protected StateEnumerator						enumerator;
	
	
	/**
	 * Constructs.
	 * @param inputDomain the input domain to be wrapped
	 * @param hashingFactory the hashing factory used to enumerate states from the input domain
	 */
	public TabulatedDomainWrapper(Domain inputDomain, StateHashFactory hashingFactory){
		this.inputDomain = inputDomain;
		this.enumerator = new StateEnumerator(this.inputDomain, hashingFactory);
	}
	
	/**
	 * Enumerates all reachable states from the input state to include in this tabularized domain's state space.
	 * @param from the souce state from which to find and enumerate all reachable states
	 */
	public void addReachableStatesFrom(State from){
		this.enumerator.findReachableStatesAndEnumerate(from);
	}
	
	
	@Override
	public Domain generateDomain() {
		
		this.tabDomain = new SADomain();
		
		Attribute att = new Attribute(this.tabDomain, ATTSTATE, AttributeType.INT);
		att.setLims(0, this.enumerator.numStatesEnumerated()-1);
		
		ObjectClass oc = new ObjectClass(this.tabDomain, CLASSSTATE);
		oc.addAttribute(att);
		
		for(Action srcAction : this.inputDomain.getActions()){
			if(srcAction.getParameterClasses().length > 0){
				throw new RuntimeException("Tabularized domain cannot wrap domains with parameterized actions");
			}
			Action tabAction = new ActionWrapper(tabDomain, srcAction);
		}
		
		return tabDomain;
	}
	
	/**
	 * Returns the state id for a state beloning to the input source domain
	 * @param s the source domain state
	 * @return the state id
	 */
	public int getStateId(State s){
		return s.getFirstObjectOfClass(CLASSSTATE).getDiscValForAttribute(ATTSTATE);
	}
	
	/**
	 * Returns the source domain state associated with the tabularized state
	 * @param s the tabularized state
	 * @return the source domain state
	 */
	public State getSourceDomainState(State s){
		int id = this.getStateId(s);
		return this.enumerator.getStateForEnumertionId(id);
	}
	
	/**
	 * Returns a tabularized state for a source domain state
	 * @param s the source domain state
	 * @return the tabularized state
	 */
	public State getTabularizedState(State s){
		int id = this.enumerator.getEnumeratedID(s);
		State ts = new State();
		ObjectInstance o = new ObjectInstance(this.tabDomain.getObjectClass(CLASSSTATE), "state");
		o.setValue(ATTSTATE, id);
		ts.addObject(o);
		return ts;
	}
	
	
	/**
	 * An action wrapper that coverts a tabularized state into the source domain state, perform the corresponding source domain action on it getting the
	 * resutling source domain state and returns the tabularized version of the resulting source domain state. Also wraps the preconditions and transition
	 * dynamics of the source domain action in similar ways.
	 * @author James MacGlashan
	 *
	 */
	public class ActionWrapper extends Action{

		protected Action srcAction;
		
		/**
		 * Constructs
		 * @param domain the tabularized domain
		 * @param action the source domain action to be wrapped
		 */
		public ActionWrapper(Domain domain, Action action){
			super(action.getName(), domain, "");
			this.srcAction = action;
		}
		
		@Override
		public boolean applicableInState(State s, String [] params){
			return srcAction.applicableInState(TabulatedDomainWrapper.this.getSourceDomainState(s), params);
		}
		
		@Override
		protected State performActionHelper(State s, String[] params) {
			
			State srcState = TabulatedDomainWrapper.this.getSourceDomainState(s);
			State srcNextState = srcAction.performAction(srcState, params);
			State tabState = TabulatedDomainWrapper.this.getTabularizedState(srcNextState);
			
			return tabState;
		}
		
		
		@Override
		public List<TransitionProbability> getTransitions(State s, String [] params){
			
			State srcState = TabulatedDomainWrapper.this.getSourceDomainState(s);
			List<TransitionProbability> srcTPs = this.srcAction.getTransitions(srcState, params);
			List<TransitionProbability> tabTPs = new ArrayList<TransitionProbability>(srcTPs.size());
			for(TransitionProbability stp : srcTPs){
				TransitionProbability ttp = new TransitionProbability(TabulatedDomainWrapper.this.getTabularizedState(stp.s), stp.p);
				tabTPs.add(ttp);
			}
			
			return tabTPs;
		}
		
	}

}
