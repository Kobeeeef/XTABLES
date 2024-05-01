"use client"

import 'primereact/resources/themes/saga-blue/theme.css'; //theme
import 'primereact/resources/primereact.min.css'; //core css
import 'primeicons/primeicons.css';


import React, {useEffect, useRef, useState} from 'react';
import {DataTable} from 'primereact/datatable';
import {FilterMatchMode} from 'primereact/api';
import {Column} from 'primereact/column';


import {Message} from 'primereact/message';


import {Toast} from 'primereact/toast';
import {InputText} from "primereact/inputtext";
import Logs from '../Components/Logs';
import validateKey from '../Utilities/KeyValidator'
import {Terminal} from 'primereact/terminal';
import {TerminalService} from 'primereact/terminalservice';

import {Dialog} from 'primereact/dialog';

import {Menubar} from 'primereact/menubar';

import {Splitter, SplitterPanel} from 'primereact/splitter';
import {Timeline} from 'primereact/timeline';

import {BlockUI} from 'primereact/blockui';

export default function Main() {
    const [rawJSON, setRawJSON] = useState({});
    const [products, setProducts] = useState([]);
    const [expandedRows, setExpandedRows] = useState(null);
    const [globalFilterValue, setGlobalFilterValue] = useState('');
    const [socket, setSocket] = useState(null);
    const toast = useRef(null);
    const [loading, setLoading] = useState(true);
    const [messages, setMessages] = useState(["Waiting for message..."]);
    const [filters, setFilters] = useState({
        global: {value: null, matchMode: FilterMatchMode.CONTAINS},
        name: {value: null, matchMode: FilterMatchMode.STARTS_WITH},
        key: {value: null, matchMode: FilterMatchMode.STARTS_WITH}
    });

    const helpMessage = `Available Commands: - clear: Clear the terminal screen. - put {key} {value}: Update a specific key value. - get {key}: Retrieve a value from the server. - sync: Syncs all data from server to refresh. - help: Show available commands and their descriptions.
`;
    useEffect(() => {
        function connect() {
            setLoading(true)
            const socket = new WebSocket('ws://localhost:8080/websocket');
            socket.onopen = () => {
                setSocket(socket)
                setLoading(false)
                console.log('WebSocket connection established');
            }
            socket.onmessage = (event) => {
                //let formatted = convertJSON(originalJSON);
                let parsed = JSON.parse(event.data);
                let parsedValue = JSON.parse(parsed.value);
                let type = parsed.type;
                if (type === "ALL") {
                    if (parsedValue == null) parsedValue = {};
                    setRawJSON(parsedValue)
                } else if (type === "UPDATE") {
                    let key = parsedValue.key;
                    let value = parsedValue.value;
                    setRawJSON(prev => {
                        return editJSONObject(prev, key, value)
                    });
                } else if (type === "MESSAGES") {
                    if (parsedValue.length > 0) {
                        setMessages([...new Set(parsedValue)].reverse());
                    }
                }
            }
            socket.onerror = (error) => {
                console.error('WebSocket error:', error);
                socket.close()
            }
            socket.onclose = () => {
                setLoading(true)
                console.log('WebSocket connection closed');
                return connect();
            }
        }

        connect();
    }, []);

    useEffect(() => {
        const commandHandler = (text) => {
            let argsIndex = text.indexOf(' ');
            let command = argsIndex !== -1 ? text.substring(0, argsIndex) : text;
            let tokens = text.split(" ");
            switch (command) {
                case 'help':
                case 'ls':
                    TerminalService.emit('response', helpMessage);
                    break;
                case 'clear':
                    TerminalService.emit('clear');
                    break;
                case 'put':
                    if (!(tokens.length >= 3)) {
                        TerminalService.emit('response', "Invalid command usage!");
                    } else if (validateKey(tokens[1]) !== null) {
                        TerminalService.emit('response', validateKey(tokens[1]));
                    } else {
                        setLoading(true)
                        TerminalService.emit('response', "Sending put request...");
                        sendMessageAndWaitForCondition({
                            type: "UPDATE", value: tokens.slice(2).join(" "), key: tokens[1]
                        }, (response) => response.type === "UPDATE_RESPONSE").then(response => {
                            setLoading(false)
                            let value = response.value;
                            TerminalService.emit('response', "Server responded with: " + value);
                        }).catch(error => {
                            setLoading(false)
                            TerminalService.emit('response', "Failed to put data: " + error);
                        });
                    }
                    break;
                case 'get':
                    if (!(tokens.length == 2)) {
                        TerminalService.emit('response', "Invalid command usage!");
                    } else if (validateKey(tokens[1]) !== null) {
                        TerminalService.emit('response', validateKey(tokens[1]));
                    } else {
                        TerminalService.emit('response', "Sending get request...");
                        sendMessageAndWaitForCondition({
                            type: "GET",
                            key: tokens[1]
                        }, (response) => response.type === "GET").then(response => {
                            let value = response.value;
                            TerminalService.emit('response', "" + value);
                        }).catch(error => {
                            TerminalService.emit('response', "Failed to get data: " + error);
                        });
                    }
                    break;
                case 'sync':
                    setLoading(true)
                    TerminalService.emit('response', "Syncing data...");
                    sendMessageAndWaitForCondition({type: "ALL"}, (response) => response.type === "ALL").then(response => {
                        setLoading(false)
                        TerminalService.emit('response', "Data synced!");
                    }).catch(error => {
                        setLoading(false)
                        TerminalService.emit('response', "Failed to sync data: " + error);
                    });
                    break;
                case 'ping':
                    TerminalService.emit('response', "Pinging server...");
                    sendMessageAndWaitForCondition({type: "PING"}, (response) => response.type === "PING_RESPONSE").then(response => {
                        let value = JSON.parse(response.value);
                        let networkLatencyMS = value.networkLatencyMS;
                        let roundTripLatencyMS = value.roundTripLatencyMS;
                        TerminalService.emit('response', `Network Latency = ${networkLatencyMS}ms; Round Trip Latency = ${roundTripLatencyMS}ms`);
                    }).catch(error => {
                        TerminalService.emit('response', "Failed to ping server: " + error);
                    });
                    break;
                default:
                    TerminalService.emit('response', 'Unknown command: ' + command);
                    break;
            }


        };
        TerminalService.on('command', commandHandler);

        return () => {
            TerminalService.off('command', commandHandler);
        };
    }, [socket]);

    function sendMessageAndWaitForCondition(message, conditionFunc, timeout = 1500) {

        socket.send(JSON.stringify(message));
        return new Promise((resolve, reject) => {
            const listener = event => {
                const data = JSON.parse(event.data);
                if (conditionFunc(data)) {
                    socket.removeEventListener('message', listener);
                    clearTimeout(timeoutId);
                    resolve(data);
                }
            };

            const timeoutId = setTimeout(() => {
                socket.removeEventListener('message', listener);
                reject(new Error('Timeout: Condition not met within ' + timeout + ' ms'));
            }, timeout);

            socket.addEventListener('message', listener);
        });
    }

    function editJSONObject(obj, key, value) {
        const keys = key.split('.');
        let currentObj = JSON.parse(JSON.stringify(obj));

        let temp = currentObj; // Keep a reference to the original object

        for (let i = 0; i < keys.length; i++) {
            const currentKey = keys[i];
            if (!temp[currentKey]) {
                // If the key doesn't exist, create a new object
                temp[currentKey] = {};
            }
            if (i === keys.length - 1) {
                // If it's the last key, update the value
                temp[currentKey].value = value;
            } else {
                // If it's not the last key, traverse deeper into the object
                if (!temp[currentKey].data) {
                    temp[currentKey].data = {};
                }
                temp = temp[currentKey].data; // Update the reference to the current level
            }
        }

        return currentObj; // Return the updated object
    }

    useEffect(() => {
        setProducts(convertJSON(rawJSON));
    }, [rawJSON]);

    function convertJSON(json) {
        const transformRecursively = (obj, parentKey = '') => {
            return Object.entries(obj).map(([key, value]) => {
                // Create a new object for the array
                let transformed = {
                    key: parentKey ? `${parentKey}.${key}` : key, name: key
                };

                if (typeof value === 'object' && value !== null) {
                    if (value.hasOwnProperty('value')) {
                        transformed.value = value.value;
                    }
                    // Recurse if there's nested data
                    if (value.data) {
                        transformed.data = transformRecursively(value.data, transformed.key);
                    }
                } else {
                    transformed.value = value;
                }

                return transformed;
            });
        };

        // Start the transformation with the top-level keys
        return transformRecursively(json);
    }


    const textEditor = (options) => {
        return <InputText type="text" value={options.value} autoFocus={true}
                          onChange={(e) => options.editorCallback(e.target.value)}
                          onKeyDown={(e) => e.stopPropagation()}/>;
    };

    const allowExpansion = (rowData) => {
        return rowData.data && Object.keys(rowData.data).length > 0;
    };
    const onCellEditComplete = (e) => {
        let {rowData, newValue, field, originalEvent: event} = e;

        if (field === "value") {
            let key = rowData.key;
            socket.send(JSON.stringify({type: "UPDATE", value: newValue, key: key}));
            toast.current.show({
                severity: 'info', summary: 'Request Sent!', detail: "The request is now queued!", life: 3000
            })
        }
        return true;
    }

    const rowExpansionTemplate = (data) => {
        return (<div className="p-3">
                <DataTable showGridlines value={data.data} editMode={"cell"} expandedRows={expandedRows}
                           onRowToggle={(e) => setExpandedRows(e.data)}
                           rowExpansionTemplate={rowExpansionTemplate}
                           dataKey="key" removableSort>
                    <Column expander={allowExpansion} style={{width: '5rem'}}/>
                    <Column field="name" header="" sortable/>
                    <Column field="value" header="" frozen={true} className="font-bold" editor={textEditor}
                            onCellEditComplete={onCellEditComplete}
                            sortable/>
                </DataTable>
            </div>);
    };

    const onGlobalFilterChange = (e) => {
        const value = e.target.value;
        let _filters = {...filters};

        _filters['global'].value = value;

        setFilters(_filters);
        setGlobalFilterValue(value);
    };


    const renderHeader = () => {
        return (<div>


                <div>
                    <div className="relative flex items-center mt-2">
        <span className="absolute">
        </span>
                        <input
                            type="text"
                            id="global_search"
                            placeholder="Name Search"
                            value={globalFilterValue}
                            onChange={onGlobalFilterChange}
                            className="block w-full py-2.5 text-gray-700 placeholder-gray-400/70 bg-white border border-gray-200 rounded-lg pl-11 pr-5 rtl:pr-11 rtl:pl-5 dark:bg-gray-900 dark:text-gray-300 dark:border-gray-600 focus:border-blue-400 dark:focus:border-blue-300 focus:ring-blue-300 focus:outline-none focus:ring focus:ring-opacity-40"
                        />
                    </div>
                </div>
            </div>);
    };
    const header = renderHeader();
    const [pingEvents, setPingEvents] = useState([]);
    const [pingDialogShown, setPingDialogShown] = useState(false);
    return (<BlockUI blocked={loading} fullScreen>
            <Toast ref={toast} position="bottom-center"/>
            <Menubar start={<img width={35} className="mr-2 rounded-xl" alt="logo" src={"/favicon.ico"}/>} model={[{
                label: 'Sync', icon: 'pi pi-sync', command: () => {
                    setLoading(true)
                    socket.send(JSON.stringify({type: "ALL"}));
                    sendMessageAndWaitForCondition({type: "ALL"}, (a) => a.type === "ALL").then((a) => {
                        setLoading(false)
                        toast.current.show({
                            severity: 'info', summary: 'Data Synced!', detail: "The data was synced!", life: 3000
                        })
                    }).catch((e) => {
                        setLoading(false)
                        toast.current.show({
                            severity: 'error',
                            summary: 'Failed To Sync!',
                            detail: "The data could not be synced!",
                            life: 3000
                        })
                    });
                }
            }, {
                label: 'Ping', icon: 'pi pi-server', command: () => {
                    const currentTime = `${new Date().getHours().toString().padStart(2, '0')}:${new Date().getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`;

                    sendMessageAndWaitForCondition({type: "PING"}, (response) => response.type === "PING_RESPONSE").then(response => {
                        let value = JSON.parse(response.value);
                        let networkLatencyMS = value.networkLatencyMS;
                        let roundTripLatencyMS = value.roundTripLatencyMS;

                        // Calculate the time to reach the server
                        const timeToReachServer = new Date(Date.now() + roundTripLatencyMS - networkLatencyMS);
                        const timeToReachServerFormatted = `${timeToReachServer.getHours().toString().padStart(2, '0')}:${timeToReachServer.getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`;

                        // Simulate server processing time (for demonstration)

                        const events = [{status: 'Sent', date: currentTime}, {
                            status: 'Processing',
                            date: timeToReachServerFormatted
                        }, {
                            status: 'Received',
                            date: `${new Date().getHours().toString().padStart(2, '0')}:${new Date().getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`
                        },];
                        setPingEvents({
                            networkLatencyMS: networkLatencyMS, roundTripLatencyMS: roundTripLatencyMS, events: events
                        })
                        setPingDialogShown(true)

                    }).catch(error => {
                        // Handle error
                    });


                }
            },]}/>
            <div className="flex bg-gray-200">
                <Dialog header="Ping Latency Statistics" visible={pingDialogShown} style={{width: '50vw'}}
                        onHide={() => setPingDialogShown(false)}>
                    <Timeline value={pingEvents.events} opposite={(item) => item.status}
                              content={(item) => <small className="text-color-secondary">{item.date}</small>}/>
                    <hr className="my-6 border-1 border-gray-200"/>

                    <div className="grid grid-cols-2 justify-center">
                        <div className="card h-full">
                            <span className="font-semibold text-lg flex justify-center">Network Latency</span>
                            <div className="flex justify-center mt-1">
                                <div >
                                    <span
                                        className="text-4xl font-bold text-900 flex justify-center">{pingEvents.networkLatencyMS}</span>
                                    <div className="flex justify-center mt-2">
                                        <Message severity={pingEvents.networkLatencyMS < 0.3 ? "success" : pingEvents.networkLatencyMS < 0.5 ? "warn" : "error"} text={pingEvents.networkLatencyMS < 0.3 ? "OKAY" : pingEvents.networkLatencyMS < 0.5 ? "DELAYED" : "SLOW"}/>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="card h-full">
                            <span className="font-semibold flex justify-center text-lg">Round Trip Latency</span>
                            <div className="flex justify-center mt-1">
<div>
                                    <span
                                        className="text-4xl font-bold text-900 flex justify-center">{pingEvents.roundTripLatencyMS}</span>
                                    <div className="flex justify-center mt-2">
                                        <Message severity={pingEvents.roundTripLatencyMS < 0.9 ? "success" : pingEvents.roundTripLatencyMS < 1.5 ? "warn" : "error"} text={pingEvents.roundTripLatencyMS < 0.9 ? "OKAY" : pingEvents.roundTripLatencyMS < 1.5 ? "DELAYED" : "SLOW"}/>
                                    </div>
</div>

                            </div>
                        </div>


                    </div>
                </Dialog>
                <Splitter step={10} className={"w-screen"}>
                    <SplitterPanel size={60} className="flex align-items-center justify-content-center">
                        <DataTable
                            value={products}
                            showGridlines
                            editMode="cell"
                            globalFilterFields={['name', 'value']}
                            filters={filters}
                            removableSort
                            filterDisplay="row"
                            expandedRows={expandedRows}
                            loading={loading}
                            onRowToggle={(e) => setExpandedRows(e.data)}
                            rowExpansionTemplate={rowExpansionTemplate}
                            className="h-screen w-full"
                            dataKey="key"
                            header={header}
                            scrollable
                            scrollHeight={"80vh"}
                            tableStyle={{minWidth: '15rem'}}
                            emptyMessage="No Data Found"
                        >
                            <Column expander={allowExpansion} style={{width: '5rem'}}/>
                            <Column field="name" header="Name" sortable/>
                            <Column
                                field="value"
                                header="Value"
                                className="font-bold"
                                frozen={true}
                                editor={textEditor}
                                onCellEditComplete={onCellEditComplete}
                                sortable
                            />
                        </DataTable>
                    </SplitterPanel>
                    <SplitterPanel size={40} className="flex-1 align-items-center justify-content-center">
                        <div className="flex-1">
                            <Terminal
                                welcomeMessage="Welcome to XBOARD! Type help to begin."
                                prompt="XTABLES $"
                                pt={{
                                    root: 'bg-gray-900 text-white border-round h-[50vh]',
                                    prompt: 'text-gray-400 mr-2',
                                    command: 'text-primary-300',
                                    response: 'text-primary-300'
                                }}
                            />
                            <Logs initialOutput={messages}></Logs>
                        </div>
                    </SplitterPanel>
                </Splitter>
            </div>
        </BlockUI>);
}
