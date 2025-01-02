# Copyright 2016 Pinterest, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json

from deployd.types.build import Build
from deployd.types.deploy_goal import DeployGoal
from deployd.types.opcode import OperationCode
from deployd.common.types import OpCode


class PingResponse(object):
    def __init__(self, jsonValue=None) -> None:
        self.opCode = OpCode.NOOP
        self.deployGoal = None
        self.multiGoalResponse = None

        if jsonValue:
            # TODO: Only used for migration, should remove later
            if isinstance(jsonValue.get('opCode'), int):
                self.opCode = OperationCode._VALUES_TO_NAMES[jsonValue.get('opCode')]
            else:
                self.opCode = jsonValue.get('opCode')

            if jsonValue.get('deployGoal'):
                self.deployGoal = DeployGoal(jsonValue=jsonValue.get('deployGoal'))

            if jsonValue.get('multiGoalResponse'):
                self.multiGoalResponse = []
                for jsonItem in jsonValue.get('multiGoalResponse'):

                    # TODO: Only used for migration, should remove later
                    if isinstance(jsonItem.get('opCode'), int):
                        opCode = OperationCode._VALUES_TO_NAMES[jsonItem.get('opCode')]
                    else:
                        opCode = jsonItem.get('opCode')

                    deployGoal = DeployGoal(jsonValue=jsonItem.get('deployGoal'))

                    self.multiGoalResponse.append({
                        'opCode': opCode,
                        'deployGoal': deployGoal,
                    })


    def __str__(self) -> str:
        mg = self.multiGoalResponse and [
            {
                'opCode': g['opCode'],
                'deployGoal': g['deployGoal'] and g['deployGoal'].to_dict()
            } for g in self.multiGoalResponse
        ]
        d = {
            'opCode': self.opCode,
            'deployGoal': self.deployGoal and self.deployGoal.to_dict(),
            'multiGoalResponse': mg
        }
        return json.dumps(d, indent=2)
