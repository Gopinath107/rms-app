package com.ris.rms.service;

import java.util.Map;

public interface ResReqDecisionService {


	Map<String, Object> hrDecide(Long requestId, Long approverUserId, String decision, String comments);

	Map<String, Object> hrDecideGroup(Long groupId, Long approverUserId, String decision, String comments);


}
