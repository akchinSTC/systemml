package com.ibm.bi.dml.lops;

import com.ibm.bi.dml.lops.LopProperties.ExecLocation;
import com.ibm.bi.dml.lops.LopProperties.ExecType;
import com.ibm.bi.dml.lops.compile.JobType;
import com.ibm.bi.dml.parser.Expression.DataType;
import com.ibm.bi.dml.parser.Expression.ValueType;

public class AppendMSP extends Lop
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2014\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";

	public static final String OPCODE = "mappend";
	
	public AppendMSP(Lop input1, Lop input2, Lop input3, DataType dt, ValueType vt) 
	{
		super(Lop.Type.Append, dt, vt);
		init(input1, input2, input3, dt, vt);
	}
	
	public void init(Lop input1, Lop input2, Lop input3, DataType dt, ValueType vt) 
	{
		this.addInput(input1);
		input1.addOutput(this);

		this.addInput(input2);
		input2.addOutput(this);
		
		this.addInput(input3);
		input3.addOutput(this);
		
		boolean breaksAlignment = false;
		boolean aligner = false;
		boolean definesMRJob = false;
		
		lps.addCompatibility(JobType.INVALID);
		this.lps.setProperties( inputs, ExecType.SPARK, ExecLocation.ControlProgram, breaksAlignment, aligner, definesMRJob );
	}
	
	@Override
	public String toString() {

		return " AppendMSP: ";
	}

	//called when append executes in CP
	public String getInstructions(String input_index1, String input_index2, String input_index3, String output_index) 
		throws LopsException
	{
		StringBuilder sb = new StringBuilder();
		sb.append( this.lps.execType );
		sb.append( OPERAND_DELIMITOR );
		sb.append( OPCODE );
		
		sb.append( OPERAND_DELIMITOR );
		sb.append( getInputs().get(0).prepInputOperand(input_index1+""));
		
		sb.append( OPERAND_DELIMITOR );
		sb.append( getInputs().get(1).prepInputOperand(input_index2+""));
		
		sb.append( OPERAND_DELIMITOR );
		sb.append( getInputs().get(2).prepScalarInputOperand(getExecType()));
		
		sb.append( OPERAND_DELIMITOR );
		sb.append( this.prepOutputOperand(output_index+"") );
		
		return sb.toString();
	}

}
