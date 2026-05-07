alter table agent_signals add column agent_kind varchar(20) null;
alter table agent_signals add column vote varchar(10) null;
alter table agent_signals add column domain_signals_json json null;
alter table agent_signals add column llm_used varchar(80) null;

update agent_signals
   set agent_kind = case
       when agent_id in ('disclosure', 'news', 'report') then 'information'
       when agent_id in ('profit', 'cost') then 'trade'
       when agent_id = 'evaluation' then 'evaluation'
       else 'domain'
   end
 where agent_kind is null;
