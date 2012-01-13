package dml.lops;

import dml.lops.LopProperties.ExecLocation;
import dml.lops.LopProperties.ExecType;
import dml.lops.compile.JobType;
import dml.parser.Expression.*;
import dml.utils.LopsException;

/**
 * Lop to represent an aggregation.
 * It is used in rowsum, colsum, etc. 
 * @author aghoting
 */

public class Aggregate extends Lops 
{
	
	/** Aggregate operation types **/
	
	public enum OperationTypes {Sum,Product,Min,Max,Trace,DiagM2V,KahanSum,KahanTrace,Mean};	
	OperationTypes operation;
 
	private boolean isCorrectionUsed = false;
	private byte correctionLocation = -1;

	/**
	 * @param input - input lop
	 * @param op - operation type
	 */
	public Aggregate(Lops input, Aggregate.OperationTypes op, DataType dt, ValueType vt ) {
		super(Lops.Type.Aggregate, dt, vt);
		init ( input, op, dt, vt, ExecType.MR );
	}
	
	public Aggregate(Lops input, Aggregate.OperationTypes op, DataType dt, ValueType vt, ExecType et ) {
		super(Lops.Type.Aggregate, dt, vt);
		init ( input, op, dt, vt, et );
	}
	
	private void init (Lops input, Aggregate.OperationTypes op, DataType dt, ValueType vt, ExecType et ) {
		operation = op;	
		this.addInput(input);
		input.addOutput(this);
		
		boolean breaksAlignment = false;
		boolean aligner = false;
		boolean definesMRJob = false;
		
		if ( et == ExecType.MR ) {
			lps.addCompatibility(JobType.GMR);
			lps.addCompatibility(JobType.RAND);
			lps.addCompatibility(JobType.REBLOCK_BINARY);
			lps.addCompatibility(JobType.REBLOCK_TEXT);
			this.lps.setProperties( ExecLocation.Reduce, breaksAlignment, aligner, definesMRJob );
		}
		else {
			lps.addCompatibility(JobType.INVALID);
			this.lps.setProperties( ExecLocation.ControlProgram, breaksAlignment, aligner, definesMRJob );
		}
	}
	
	// this function must be invoked during hop-to-lop translation
	public void setupCorrectionLocation(byte loc) {
		if ( operation == OperationTypes.KahanSum || operation == OperationTypes.KahanTrace || operation == OperationTypes.Mean ) {
			isCorrectionUsed = true;
			correctionLocation = loc;
		}
	}
	
	/**
	 * for debugging purposes. 
	 */
	
	public String toString()
	{
		return "Operation: " + operation;		
	}

	/**
	 * method to get operation type
	 * @return
	 */
	 
	public OperationTypes getOperationType()
	{
		return operation;
	}
	
	@Override
	public String getInstructions(int input_index, int output_index) throws LopsException
	{
		boolean isCorrectionApplicable = false;
		
		String opString = new String("");
		switch(operation) {
		case Sum: 
		case Trace: 
			opString += "a+"; break;
		case Mean: 
			isCorrectionApplicable = true; 
			opString += "amean"; 
			break;
		case Product: 
			opString += "a*"; break;
		case Min: 
			opString += "amin"; break;
		case Max: 
			opString += "amax"; break;
		
		case KahanSum:
		case KahanTrace: 
			isCorrectionApplicable = true; 
			opString += "ak+"; 
			break;
		default:
			throw new UnsupportedOperationException("Instruction is not defined for Aggregate operation: " + operation);
		}
		
		String inst = new String("");
		inst += opString + OPERAND_DELIMITOR + 
		        input_index + VALUETYPE_PREFIX + this.getInputs().get(0).get_valueType() + OPERAND_DELIMITOR + 
		        output_index + VALUETYPE_PREFIX + this.get_valueType() ;
		
		if ( isCorrectionApplicable )
			// add correction information to the instruction
			inst += OPERAND_DELIMITOR + isCorrectionUsed + OPERAND_DELIMITOR + correctionLocation;
		
		return inst;
	}

 
 
}
