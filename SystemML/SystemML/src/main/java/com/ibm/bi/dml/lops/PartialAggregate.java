/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2014
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.lops;

import com.ibm.bi.dml.hops.HopsException;
import com.ibm.bi.dml.lops.LopProperties.ExecLocation;
import com.ibm.bi.dml.lops.LopProperties.ExecType;
import com.ibm.bi.dml.lops.compile.JobType;
import com.ibm.bi.dml.parser.Expression.*;


/**
 * Lop to perform a partial aggregation. It was introduced to do some initial
 * aggregation operations on blocks in the mapper/reducer.
 */

public class PartialAggregate extends Lop 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2013\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	public enum DirectionTypes {
		RowCol, 
		Row, 
		Col
	};

	public enum CorrectionLocationType { 
		NONE, 
		LASTROW, 
		LASTCOLUMN, 
		LASTTWOROWS, 
		LASTTWOCOLUMNS, 
		INVALID 
	};
	
	private Aggregate.OperationTypes operation;
	private DirectionTypes direction;
	private boolean _dropCorr = false;
	
	//optional attribute for spark exec type
	private boolean _aggregate = true;
	
	
	public PartialAggregate( Lop input, Aggregate.OperationTypes op,
			PartialAggregate.DirectionTypes direct, DataType dt, ValueType vt)
		throws LopsException 
	{
		super(Lop.Type.PartialAggregate, dt, vt);
		init(input, op, direct, dt, vt, ExecType.MR);
	}

	public PartialAggregate( Lop input, Aggregate.OperationTypes op,
			PartialAggregate.DirectionTypes direct, DataType dt, ValueType vt, ExecType et)
		throws LopsException 
	{
		super(Lop.Type.PartialAggregate, dt, vt);
		init(input, op, direct, dt, vt, et);
	}
	
	public PartialAggregate( Lop input, Aggregate.OperationTypes op,
			PartialAggregate.DirectionTypes direct, DataType dt, ValueType vt, boolean aggregate, ExecType et)
		throws LopsException 
	{
		super(Lop.Type.PartialAggregate, dt, vt);
		init(input, op, direct, dt, vt, et);
		_aggregate = aggregate;
	}
	
	/**
	 * Constructor to setup a partial aggregate operation.
	 * 
	 * @param input
	 * @param op
	 * @return 
	 * @throws LopsException
	 */
	private void init(Lop input,
			Aggregate.OperationTypes op,
			PartialAggregate.DirectionTypes direct, DataType dt, ValueType vt, ExecType et) {
		operation = op;
		direction = direct;
		this.addInput(input);
		input.addOutput(this);

		boolean breaksAlignment = true;
		boolean aligner = false;
		boolean definesMRJob = false;
		
		if ( et == ExecType.MR ) 
		{
			/*
			 * This lop CAN NOT be executed in PARTITION, SORT, STANDALONE MMCJ:
			 * only in mapper.
			 */
			lps.addCompatibility(JobType.GMR);
			lps.addCompatibility(JobType.DATAGEN);
			lps.addCompatibility(JobType.REBLOCK);
			lps.addCompatibility(JobType.MMCJ);
			lps.addCompatibility(JobType.MMRJ);
			this.lps.setProperties(inputs, et, ExecLocation.Map, breaksAlignment, aligner, definesMRJob);
		} 
		else //CP | SPARK
		{
			lps.addCompatibility(JobType.INVALID);
			this.lps.setProperties(inputs, et, ExecLocation.ControlProgram, breaksAlignment, aligner, definesMRJob);
		}
	}

	public void setDropCorrection()
	{
		_dropCorr = true;
	}
	
	public static CorrectionLocationType decodeCorrectionLocation(String loc) throws LopsException {
		if ( loc.equals("NONE") )
			return CorrectionLocationType.NONE;
		else if ( loc.equals("LASTROW") )
			return CorrectionLocationType.LASTROW;
		else if ( loc.equals("LASTCOLUMN") )
			return CorrectionLocationType.LASTCOLUMN;
		else if ( loc.equals("LASTTWOROWS") )
			return CorrectionLocationType.LASTTWOROWS;
		else if ( loc.equals("LASTTWOCOLUMNS") )
			return CorrectionLocationType.LASTTWOCOLUMNS;
		else 
			throw new LopsException("In PartialAggregate Lop, Unrecognized correction location: " + loc);
	}
	/**
	 * This method computes the location of "correction" terms in the output
	 * produced by PartialAgg instruction.
	 * 
	 * When computing the stable sum, "correction" refers to the compensation as
	 * defined by the original Kahan algorithm. When computing the stable mean,
	 * "correction" refers to two extra values (the running mean, count)
	 * produced by each Mapper i.e., by each PartialAgg instruction.
	 * 
	 * This method is invoked during hop-to-lop translation, while creating the
	 * corresponding Aggregate lop
	 * 
	 * Computed information is encoded in the PartialAgg instruction so that the
	 * appropriate aggregate operator is used at runtime (see:
	 * dml.runtime.matrix.operator.AggregateOperator.java and dml.runtime.matrix)
	 */
	public CorrectionLocationType getCorrectionLocation() throws LopsException {

		CorrectionLocationType loc;

		switch (operation) {
		case KahanSum:
		case KahanTrace:
			switch (direction) {
			case Col:
				// colSums: corrections will be present as a last row in the
				// result
				loc = CorrectionLocationType.LASTROW;
				break;
			case Row:
			case RowCol:
				// rowSums, sum: corrections will be present as a last column in
				// the result
				loc = CorrectionLocationType.LASTCOLUMN;
				break;
			default:
				throw new LopsException(this.printErrorLocation() + 
						"in PartialAggregate Lop,  getCorrectionLocation() Unknown aggregarte direction - "
								+ direction);
			}
			break;

		case Mean:
			// Computation of stable mean requires each mapper to output both
			// the running mean as well as the count
			switch (direction) {
			case Col:
				// colMeans: last row is correction 2nd last is count
				loc = CorrectionLocationType.LASTTWOROWS;
				break;
			case Row:
			case RowCol:
				// rowMeans, mean: last column is correction 2nd last is count
				loc = CorrectionLocationType.LASTTWOCOLUMNS;
				break;
			default:
				throw new LopsException( this.printErrorLocation() + 
						"getCorrectionLocaion():: Unknown aggregarte direction - "
								+ direction);
			}
			break;
			
		case MaxIndex:
		case MinIndex:
			loc = CorrectionLocationType.LASTCOLUMN;
			break;
			
		default:
			loc = CorrectionLocationType.NONE;
		}
		return loc;
	}

	public void setDimensionsBasedOnDirection(long dim1, long dim2,  
			long rowsPerBlock, long colsPerBlock) throws LopsException {
		try {
		if (direction == DirectionTypes.Row)
			outParams.setDimensions(dim1, 1, rowsPerBlock, colsPerBlock, -1);
		else if (direction == DirectionTypes.Col)
			outParams.setDimensions(1, dim2, rowsPerBlock, colsPerBlock, -1);
		else if (direction == DirectionTypes.RowCol)
			outParams.setDimensions(1, 1, rowsPerBlock, colsPerBlock, -1);
		else
			throw new LopsException(this.printErrorLocation() + "In PartialAggregate Lop, Unknown aggregate direction " + direction);
		} catch (HopsException e) {
			throw new LopsException(this.printErrorLocation() + "In PartialAggregate Lop, error setting dimensions based on direction", e);
		}
	}

	public String toString() {
		return "Partial Aggregate " + operation;
	}
	
	private String getOpcode() {
		return getOpcode(operation, direction);
	}

	/**
	 * Instruction generation for for CP and Spark
	 */
	@Override
	public String getInstructions(String input1, String output) 
		throws LopsException 
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( OPERAND_DELIMITOR );
		sb.append( getOpcode() );
		sb.append( OPERAND_DELIMITOR );
		sb.append( getInputs().get(0).prepInputOperand(input1) );
		sb.append( OPERAND_DELIMITOR );
		sb.append( this.prepOutputOperand(output) );
		
		//in case of spark, we also compile the optional aggregate flag into the instruction.
		if( getExecType() == ExecType.SPARK ) {
			sb.append( OPERAND_DELIMITOR );
			sb.append( _aggregate );	
		}
		
		return sb.toString();
	}
	
	@Override
	public String getInstructions(int input_index, int output_index)
		throws LopsException 
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( Lop.OPERAND_DELIMITOR );
		sb.append( getOpcode() );
		sb.append( OPERAND_DELIMITOR );
		sb.append( getInputs().get(0).prepInputOperand(input_index) );
		sb.append( OPERAND_DELIMITOR );
		sb.append( this.prepOutputOperand(output_index) );
		sb.append( OPERAND_DELIMITOR );
		sb.append( _dropCorr );

		return sb.toString();
	}

	/**
	 * 
	 * @param op
	 * @param dir
	 * @return
	 */
	public static String getOpcode(Aggregate.OperationTypes op, DirectionTypes dir) 
	{
		switch( op )
		{
			case Sum: {
				if( dir == DirectionTypes.RowCol ) 
					return "ua+";
				else if( dir == DirectionTypes.Row ) 
					return "uar+";
				else if( dir == DirectionTypes.Col ) 
					return "uac+";
				break;
			}

			case Mean: {
				if( dir == DirectionTypes.RowCol ) 
					return "uamean";
				else if( dir == DirectionTypes.Row ) 
					return "uarmean";
				else if( dir == DirectionTypes.Col ) 
					return "uacmean";
				break;
			}			
			
			case KahanSum: {
				// instructions that use kahanSum are similar to ua+,uar+,uac+
				// except that they also produce correction values along with partial
				// sums.
				if( dir == DirectionTypes.RowCol )
					return "uak+";
				else if( dir == DirectionTypes.Row )
					return "uark+";
				else if( dir == DirectionTypes.Col ) 
					return "uack+";
				break;
			}
			
			case Product: {
				if( dir == DirectionTypes.RowCol )
					return "ua*";
				break;
			}
			
			case Max: {
				if( dir == DirectionTypes.RowCol ) 
					return "uamax";
				else if( dir == DirectionTypes.Row ) 
					return "uarmax";
				else if( dir == DirectionTypes.Col )
					return "uacmax";
				break;
			}
			
			case Min: {
				if( dir == DirectionTypes.RowCol ) 
					return "uamin";
				else if( dir == DirectionTypes.Row ) 
					return "uarmin";
				else if( dir == DirectionTypes.Col ) 
					return "uacmin";
				break;
			}
			
			case MaxIndex:{
				if( dir == DirectionTypes.Row )
					return "uarimax";
				break;
			}
			
			case MinIndex: {
				if( dir == DirectionTypes.Row )
					return "uarimin";
				break;
			}
		
			case Trace: {
				if( dir == DirectionTypes.RowCol)
					return "uatrace";
				break;	
			}
			
			case KahanTrace: {
				if( dir == DirectionTypes.RowCol ) 
					return "uaktrace";
				break;
			}
		}
		
		//should never come here for normal compilation
		throw new UnsupportedOperationException("Instruction is not defined for PartialAggregate operation " + op);
	}

}