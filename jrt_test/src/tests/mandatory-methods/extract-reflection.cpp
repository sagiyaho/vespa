// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/app.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/vespalib/util/time.h>
#include <thread>

class RPCInfo : public FastOS_Application
{
public:

  void GetReq(FRT_RPCRequest **req, FRT_Supervisor *supervisor)
  {
    if ((*req) != nullptr)
      (*req)->SubRef();
    (*req) = supervisor->AllocRPCRequest();
  }

  void FreeReqs(FRT_RPCRequest *r1, FRT_RPCRequest *r2)
  {
    if (r1 != nullptr)
      r1->SubRef();
    if (r2 != nullptr)
      r2->SubRef();
  }

  void DumpMethodInfo(const char *indent, FRT_RPCRequest *info,
                      const char *name)
  {
    if (info->IsError()) {
      printf("%sMETHOD %s\n", indent, name);
      printf("%s  [error(%d): %s]\n\n", indent,
             info->GetErrorCode(),
             info->GetErrorMessage());
      return;
    }

    const char      *desc    = info->GetReturn()->GetValue(0)._string._str;
    const char      *arg     = info->GetReturn()->GetValue(1)._string._str;
    const char      *ret     = info->GetReturn()->GetValue(2)._string._str;
    uint32_t         argCnt  = strlen(arg);
    uint32_t         retCnt  = strlen(ret);
    FRT_StringValue *argName = info->GetReturn()->GetValue(3)._string_array._pt;
    FRT_StringValue *argDesc = info->GetReturn()->GetValue(4)._string_array._pt;
    FRT_StringValue *retName = info->GetReturn()->GetValue(5)._string_array._pt;
    FRT_StringValue *retDesc = info->GetReturn()->GetValue(6)._string_array._pt;

    printf("%sMETHOD %s\n", indent, name);
    printf("%s  DESCRIPTION:\n"
           "%s    %s\n", indent, indent, desc);

    if (argCnt > 0) {
      printf("%s  PARAMS:\n", indent);
      for (uint32_t a = 0; a < argCnt; a++)
        printf("%s    [%c][%s] %s\n", indent, arg[a], argName[a]._str,
               argDesc[a]._str);
    }

    if (retCnt > 0) {
      printf("%s  RETURN:\n", indent);
      for (uint32_t r = 0; r < retCnt; r++)
        printf("%s    [%c][%s] %s\n", indent, ret[r], retName[r]._str,
               retDesc[r]._str);
    }
    printf("\n");
  }


  int Main() override
  {
    if (_argc < 2) {
      printf("usage : rpc_info <connectspec> [verbose]\n");
      return 1;
    }

    bool verbose = (_argc > 2 && strcmp(_argv[2], "verbose") == 0);
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor & supervisor = server.supervisor();
    FRT_Target     *target = supervisor.GetTarget(_argv[1]);
    FRT_RPCRequest *m_list = nullptr;
    FRT_RPCRequest *info   = nullptr;

    for (int i = 0; i < 50; i++) {
        GetReq(&info, &supervisor);
        info->SetMethodName("frt.rpc.ping");
        target->InvokeSync(info, 60.0);
        if (info->GetErrorCode() != FRTE_RPC_CONNECTION) {
            break;
        }
        std::this_thread::sleep_for(1s);
        target->SubRef();
        target = supervisor.GetTarget(_argv[1]);
    }
    if (info->IsError()) {
        fprintf(stderr, "Error talking to %s\n", _argv[1]);
        info->Print();
        FreeReqs(m_list, info);
        return 1;
    }

    GetReq(&m_list, &supervisor);
    m_list->SetMethodName("frt.rpc.getMethodList");
    target->InvokeSync(m_list, 60.0);

    if (!m_list->IsError()) {

      uint32_t numMethods      = m_list->GetReturn()->GetValue(0)._string_array._len;
      FRT_StringValue *methods = m_list->GetReturn()->GetValue(0)._string_array._pt;
      FRT_StringValue *arglist = m_list->GetReturn()->GetValue(1)._string_array._pt;
      FRT_StringValue *retlist = m_list->GetReturn()->GetValue(2)._string_array._pt;

      for (uint32_t m = 0; m < numMethods; m++) {

        if (verbose) {

          GetReq(&info, &supervisor);
          info->SetMethodName("frt.rpc.getMethodInfo");
          info->GetParams()->AddString(methods[m]._str);
          target->InvokeSync(info, 60.0);
          DumpMethodInfo("", info, methods[m]._str);

        } else {

          printf("METHOD [%s] <- %s <- [%s]\n",
                 retlist[m]._str, methods[m]._str, arglist[m]._str);
        }
      }
    } else {
      fprintf(stderr, "  [error(%d): %s]\n",
              m_list->GetErrorCode(),
              m_list->GetErrorMessage());
    }
    FreeReqs(m_list, info);
    target->SubRef();
    return 0;
  }
};


int
main(int argc, char **argv)
{
  RPCInfo myapp;
  return myapp.Entry(argc, argv);
}
