var API_ENDPOINT = "REPLACE ENDPOINT HERE";

$( document ).ready(function() {
	function validateMatch() {
		if($('#documentId').html() != "Not submitted") {
			if($('#calculatedHash').html() == $('#hashInDB').html() && $('#calculatedHash').html() == $('#hashInEth').html()) {
				$('#matchStatus').html("<span class=\"match\">Match - Document is valid.</span>");
			}
			else if($('#hashInDB').html() == "processing") {
				$('#matchStatus').html("<span>Unknown - Document is not yet processed.</span>");
			}
			else {
				$('#matchStatus').html("<span class=\"mismatch\">Mismatch - Document has been tampered with.</span>");
			}
		}
	}
	
	$("#postText").on('change keyup paste', function() {
		var hash = sha3_256($('#postText').val());
		$('#calculatedHash').html(hash);
		validateMatch();
	});
	
	$("#clearButton").bind( "click", function() {
		clearDoc();
	});
	
	$("#submitButton").bind( "click", function() {
		var inputData = {
			"text" : $('#postText').val()
		};
		
		// Clear the text already
		clearDoc();	
		$("#submitting").css('display', 'block');
		
		$.ajax({
			url: API_ENDPOINT,
			type: 'POST',
			data:  JSON.stringify(inputData)  ,
			contentType: 'application/json; charset=utf-8',
			success: function (response) {
				getAllDocs();
				$("#submitting").css('display', 'none');
			},
			error: function () {
				console.log("Error submitting. Retry.");
				$.ajax({
					url: API_ENDPOINT,
					type: 'POST',
					data:  JSON.stringify(inputData)  ,
					contentType: 'application/json; charset=utf-8',
					success: function (response) {
						getAllDocs();
						$("#submitting").css('display', 'none');
					},
					error: function () {
						console.log("Error submitting. Process is dead.");
						
						$("#submitting").css('display', 'none');
					}
				});
			}
		});
	});
	
	// Get all documents
	function getAllDocs() {
		$.ajax({
			url: API_ENDPOINT + '?id=*',
			type: 'GET',
			success: function (response) {
				//alert(response);
				$('#documents tr').slice(1).remove();
				var obj = JSON.parse(response);
				jQuery.each(obj, function(i,data) {
					
					if(data['hash'] == "processing") {
						$("#documents").append("<tr><td id=\"" + data['id'] + "\" class=\"clickable\">" + data['id'] + "</td> <td id=\"load_" + data['id'] + "\"><div class=\"loader\"></div></td></tr><tr><td colspan=\"2\">" + data['text'] + "</td> </tr>");
						var myfunction;
						
						myfunction = setTimeout(function(){ refreshHash(data['id'], myfunction); }, 3000);
						
					}
					else {
						$("#documents").append("<tr><td id=\"" + data['id'] + "\" class=\"clickable\">" + data['id'] + "</td> <td>" + data['hash'] + "</td></tr><tr><td colspan=\"2\">" + data['text'] + "</td> </tr>");
					}
					
					$("#" + data['id'] ).bind( "click", function() {
						loadDoc(data['id']);
					});
				});
			},
			error: function () {
				console.log("Error fetching docs.");
			}
		});
	}
		
	function loadDoc(id) {
		$.ajax({
			url: API_ENDPOINT + '?id=' + id,
			type: 'GET',
			success: function (response) {
				var obj = JSON.parse(response);

				$('#postText').val(obj[0]['text']);
				$('#documentId').html(obj[0]['id']);
				$('#hashInDB').html(obj[0]['hash']);
				$('#hashInEth').html("Waiting...");
				$('#matchStatus').html("Waiting...");
				
				$.ajax({
					url: API_ENDPOINT + '?reference=' + obj[0]['id'],
					type: 'GET',
					success: function (response) {
						var block = JSON.parse(response);
						$('#hashInEth').html(block[0]["hash"]);
						validateMatch();
					},
					error: function () {
						alert("error");
					}
				});
				
				var hash = sha3_256($('#postText').val());
				$('#calculatedHash').html(hash);
			},
			error: function () {
				console.log("Error fetching doc " + id);
			}
		});
	}
	
	function clearDoc() {
		$('#postText').val("");
		$('#documentId').html("Not submitted");
		$('#hashInDB').html("Not submitted");
		$('#hashInEth').html("Not submitted");
		$('#matchStatus').html("Unknown");
		var hash = sha3_256($('#postText').val());
		$('#calculatedHash').html(hash);
	}
	
	function refreshHash(id, myfunction) {
		$.ajax({
			url: API_ENDPOINT + '?reference=' + id,
			type: 'GET',
			success: function (response) {
				var block = JSON.parse(response);
				if(block[0]["hash"] != "") {
					$('#load_' + id).html(block[0]['hash']);
					clearTimeout(myfunction);
				}
				else {
					var myfunction;
					myfunction = setTimeout(function(){ refreshHash(id, myfunction); }, 3000);
				}
			},
			error: function () {
			}
		});
	}
	
	getAllDocs();
	
	var hash = sha3_256($('#postText').val());
	$('#calculatedHash').html(hash);
});

