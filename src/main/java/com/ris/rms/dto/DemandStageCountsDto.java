package com.ris.rms.dto;

import lombok.Data;

@Data
public class DemandStageCountsDto {
	private int total = 0;
	private int open = 0;
	private int approvalPending = 0;
	private int interviewScheduled = 0;
	private int interviewInProgress = 0;
	private int selected = 0;
	private int allocated = 0;
	private int rejected = 0;
	
	public void addOpen() { this.open++; }
	public void addApprovalPending() { this.approvalPending++; }
	public void addInterviewScheduled() { this.interviewScheduled++; }
	public void addInterviewInProgress() { this.interviewInProgress++; }
	public void addSelected() { this.selected++; }
	public void addAllocated() { this.allocated++; }
	public void addRejected() { this.rejected++; }
}