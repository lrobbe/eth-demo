pragma solidity ^0.4.24;

contract Validator {
    address owner;
    mapping(bytes32 => string) map;
	
    modifier onlyOwner(){
        require(msg.sender == owner);
        _;
    }
	
    function getOwner() external constant returns (address) {
        return owner;
    }
	
    constructor() public {
        owner = msg.sender;
    }
	
    function kill() external onlyOwner {
        selfdestruct(owner);
    }
	
    function transfer(address newowner) external onlyOwner {
        owner = newowner;
    }
	
    function add(string key, string value) external onlyOwner {
        bytes32 hash = keccak256(abi.encodePacked(key));
        if(bytes(map[hash]).length != 0) {
            revert("Document already exists");
        }
        map[hash] = value;
    }
	
    function get(string key) external constant returns(string){
        bytes32 hash = keccak256(abi.encodePacked(key));
        if(bytes(map[hash]).length == 0) {
            revert("Document does not exists");
        }
        return map[hash];
    }
}